package com.cloudflare.sslsaas;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.joshworks.restclient.http.HttpResponse;
import io.joshworks.restclient.http.RestClient;
import org.apache.http.client.config.CookieSpecs;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


public class IssueCert
{

    public static final String API_BASE_URL = "https://api.cloudflare.com/client/v4/";


    public static void main( String[] args )
    {
        Map<String, String> env = System.getenv();

        // this is the Managed CNAME zone that will serve as the container for the custom hostnames
        String parentZoneName = env.get("WHITELABELZONE");;

        // this is the hostname that's provided to customers as part of your onboarding instructions
        // this should be the same hostname (or one that CNAMEs to) the proxy fallback host provided
        // to your Cloudflare SE during onboarding of your Managed CNAME zone
        String whiteLabelHostname = env.get("WHITELABELHOST");

        // below we generate the customer's vanity/custom customerHostname based on current UTC time
        // this generated customerHostname (wildcard) resolves to whiteLabelHostname
        String customerDomain = env.get("CUSTOMERDOMAIN");
        String customerHostname = generateHostnameAndCheckResolution(customerDomain, whiteLabelHostname);

        // next, we connect to the Cloudflare API, using the API key and email from the shell environment
        String cfEmail = env.get("CF_API_EMAIL");
        String cfApiKey = env.get("CF_API_KEY");
        RestClient client = prepareCloudflareRestClient(cfApiKey, cfEmail);

        // we look up the zoneID so it can be used for the custom_hostname API call
        String zoneId = getZoneIDByName(parentZoneName, client);

        if(zoneId == null) {
            System.err.println("Could not retrieve zone info for zone : "+parentZoneName);
            System.exit(-1);
        }

        // and then we make the API call to issue a certificate using HTTP validation and print the call result
        String customHostnameId = createCustomHostname(customerHostname, zoneId, client);

        // lastly, we loop indefinitely until we see the hostname has been issued
        // note: it may never issue if the customer fails to add the CNAME
        waitForCertificateIssuance(zoneId, customHostnameId, customerHostname, client);

        printCertificateDetailsFromEdge(customerHostname);

    }


    // utility methods

    // generate a hostname for demo purposes (subdomain of input parameter)
    private static final String generateHostnameAndCheckResolution( String customerDomain, String whitelabelHostname) {

        Date now = Calendar.getInstance().getTime();


        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        String time = dateFormat.format(now);

        String customerHostname = "ex" + time + "." + customerDomain;

        System.out.println("Acquiring certificate for " + customerHostname + ".");

        // first we check to see if the customerHostname resolves to the parent zone
        if (!pointedTo(customerHostname, whitelabelHostname)) {
            System.out.println("WARNING: " + customerHostname + " does not resolve to same IP(s) as " + whitelabelHostname);
            System.out.println("Validation will not complete and certificate will not issue until CNAME has been pointed.");
        }

        return customerHostname;
    }

    // (very) hacky way to check if one hostname points to another
    // assumes that the IP address resolution is identical and sorted (may very well be, haven't checked)
    private static final boolean pointedTo( String source, String target) {
        InetAddress[] sourceIPs = null;
        try {
            sourceIPs = InetAddress.getAllByName(source);
        } catch (UnknownHostException uhe) {
            System.err.println(uhe.getMessage());
        }


        InetAddress[] targetIPs = null;

        try {
            targetIPs = InetAddress.getAllByName(source);
        } catch (UnknownHostException uhe) {
            System.err.println(uhe.getMessage());
        }

        return Arrays.equals(sourceIPs[0].getAddress(), targetIPs[0].getAddress());
    }

    // Makes a call to retrieve all zones and returns zoneID for a matching zone
    private static final String getZoneIDByName (String zoneName, RestClient client) {
        JsonObject result = request(client,HttpMethod.GET,"zones", null);
        if(result.get("success").getAsBoolean()) {
            JsonArray zones = result.get("result").getAsJsonArray();
            for (int i =0; i<zones.size(); i++) {
                JsonObject zone = zones.get(i).getAsJsonObject();
                if(zone.get("name").getAsString().equals(zoneName)) {
                    String zoneId = zone.get("id").getAsString();
                    return zoneId;
                }
            }
        }

        return null;
    }

    // generate a hostname for demo purposes (subdomain of input parameter)
    private static final String createCustomHostname (String customerHostName, String zoneId, RestClient client) {

        String payload = "{ \"hostname\": \""+customerHostName+"\"," +
                                " \"ssl\": {\"method\": \"http\", \"type\": \"dv\"}" +
                         "}";

        JsonObject response = request(client,HttpMethod.POST, "zones/"+zoneId+"/custom_hostnames", payload);
        if(response.get("success").getAsBoolean()) {
            JsonObject customHostname = response.get("result").getAsJsonObject();
            String customHostnameID = customHostname.get("id").getAsString();
            String sslStatus = customHostname.get("ssl").getAsJsonObject().get("status").getAsString();

            System.out.println("\nAPI call to issue certificate returned with initial SSL status of "
                    +sslStatus+" (hostname ID="+customHostnameID+").");

            return customHostnameID;
        }
        return null;
    }

    private static final void waitForCertificateIssuance(String zoneID, String customHostnameID, String customerHostname, RestClient client) {
	    int sleepTimeInSeconds = 20;
        System.out.printf("\nPolling on certificate status indefinitely (will sleep %d seconds between calls):\n", sleepTimeInSeconds);
        do {
            Calendar now = Calendar.getInstance();
            System.out.printf("[ %02d:%02d:%02d ] Checking on certificate status of %s.. ",
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        now.get(Calendar.SECOND),
                        customerHostname);

            String certStatus = getCertStatus(zoneID, customHostnameID, client);
            System.out.println(certStatus);
            if (certStatus.equals( "active")) {
                break;
            }

            try {
                Thread.currentThread().sleep(sleepTimeInSeconds * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while(true);

        System.out.println("\nCertificate has been issued and is live on Cloudflare's edge:");
    }

    // get the current SSL status of a custom hostname
    private static final String getCertStatus(String zoneID, String customerHostnameID, RestClient client) {

        JsonObject response = request(client, HttpMethod.GET, "zones/"+zoneID+"/custom_hostnames/"+customerHostnameID, null);
        if(response.get("success").getAsBoolean()) {
            JsonObject customHostname = response.get("result").getAsJsonObject();
            String sslStatus = customHostname.get("ssl").getAsJsonObject().get("status").getAsString();

            return sslStatus;
        }
        return null;
    }

    // fetch the certificate and print important details about it
    private static void printCertificateDetailsFromEdge(String customHostname) {
        try {
            SSLSocketFactory factory = HttpsURLConnection.getDefaultSSLSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket(customHostname, 443);
            socket.startHandshake();
            SSLSession session = socket.getSession();
            X509Certificate cert = (X509Certificate)session.getPeerCertificates()[0];

            System.out.println("Certificate Details:");
            System.out.println(Strings.repeat("-", 70));
            System.out.printf("%-20s %-20s\n", "Serial Number", cert.getSerialNumber());
            System.out.printf("%-20s %-20s\n", "Signature Algorithm", cert.getSigAlgName());
            System.out.printf("%-20s %-20s\n", "Issue Date", cert.getNotBefore());
            System.out.printf("%-20s %-20s\n", "Expiration Date", cert.getNotAfter());

            String dn = cert.getSubjectX500Principal().getName();
            LdapName ldapDN = new LdapName(dn);
            String commonName = null;
            for(Rdn rdn : ldapDN.getRdns()) {

                if(rdn.getType().equals("CN")) {
                    commonName = rdn.getValue().toString();
                }
            }
            System.out.printf("%-20s %-20s\n", "Common Name", commonName);

            StringBuilder sans = new StringBuilder();
            Iterator sansLists = cert.getSubjectAlternativeNames().iterator();
            while(sansLists.hasNext()) {
                sans.append(((List)sansLists.next()).get(1).toString());
            }
            System.out.printf("%-20s %-20s\n", "Subject Alt. Name(s)", sans.toString());
            System.out.println(Strings.repeat("-", 70));


        } catch (Throwable t) {
            t.printStackTrace();

        }
    }

    // initializes RestClient using provided Cloudflare credentials
    private static RestClient prepareCloudflareRestClient(String authKey, String email) {
        return RestClient.builder()
            .baseUrl(API_BASE_URL)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("X-Auth-Key", authKey)
            .defaultHeader("X-Auth-Email", email)
            .followRedirect(false)
            .cookieSpec( CookieSpecs.IGNORE_COOKIES)
            .build();
    }

    // Makes an API call for a given method, path using the RestClient
    private static JsonObject request(RestClient client, HttpMethod method, String path, String body) {
        HttpResponse<String> httpResponse = null;
        switch (method) {
            case GET:
                httpResponse = client.get(path).asString();
                break;
            case POST:
                httpResponse = client.post(path).body(body).asString();
                break;
        }
        JsonElement parsed = new JsonParser().parse( httpResponse.getBody() );
        return parsed.getAsJsonObject();
    }

    private static enum HttpMethod {
        GET, POST;
    }
}
