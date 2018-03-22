<?php

require_once('vendor/autoload.php');

// this is the Managed CNAME zone that will serve as the container for the custom hostnames
$parentZoneName = getenv("WHITELABELZONE");
if (!$parentZoneName) {
	exit("WHITELABELZONE is not set.\n");
}

// this is the hostname that's provided to customers as part of your onboarding instructions
// this should be the same hostname (or one that CNAMEs to) the proxy fallback host provided
// to your Cloudflare SE during onboarding of your Managed CNAME zone
$whitelabelHostname = getenv("WHITELABELHOST");
if (!$whitelabelHostname) {
	exit("WHITELABELHOST is not set.\n");
}

// below we generate the customer's vanity/custom customerHostname based on current UTC time
// this generated customerHostname (wildcard) resolves to whiteLabelHostname
$customerDomain = getenv("CUSTOMERDOMAIN");
if (!$customerDomain) {
	exit("CUSTOMERDOMAIN is not set.\n");
}
$customerHostname = generateHostnameAndCheckResolution($customerDomain, $whitelabelHostname);

// next, we connect to the Cloudflare API, using the API key and email from the shell environment
$key = new \Cloudflare\API\Auth\APIKey(getenv("CF_API_EMAIL"), getenv("CF_API_KEY"));
$adapter = new Cloudflare\API\Adapter\Guzzle($key);

// confirm our API authentication is good
try {
	$user = new \Cloudflare\API\Endpoints\User($adapter);
	$user_id = $user->getUserID();
} catch (Exception $e) {
	exit("Unable to connect to Cloudflare API using provided CF_API_KEY and CF_API_EMAIL.\n");
}

// after connecting, we look up the zoneID so it can be used for the custom_hostname API call
$zone = new \Cloudflare\API\Endpoints\Zones($adapter);
$zoneID = $zone->getZoneID($parentZoneName);

$payloadString = '{
	"hostname": "",
	"ssl": {
		"method": "http",
		"type": "dv"
	}
}';
$payload = json_decode($payloadString);
$payload->hostname = $customerHostname;

$client = new GuzzleHttp\Client();
$res = $client->request("POST", "https://api.cloudflare.com/client/v4/zones/$zoneID/custom_hostnames", [
    'headers' => [
    	'X-Auth-Email' => getenv("CF_API_EMAIL"),
    	'X-Auth-Key' => getenv("CF_API_KEY")
    ],
    'json' => $payload
]);

$responseCode = $res->getStatusCode();
if ($responseCode != 201) {
	exit("Error creating certificate for $customerHostname (API returned $responseCode).".PHP_EOL);
}

$bodyString = $res->getBody()->getContents();
$body = json_decode($bodyString);

// commented out pending https://github.com/cloudflare/cloudflare-php/issues/50
/*
// and then we make the API call to issue a certificate using HTTP validation and print the call result
customHostnamePayload := cloudflare.CustomHostname{Hostname: customerHostname, SSL: cloudflare.CustomHostnameSSL{Method: "http", Type: "dv"}}
response, err := api.CreateCustomHostname(zoneID, customHostnamePayload)
if err != nil {
	log.Fatal(err)
}
$customHostnameID = response.Result.ID;
$sslStatus = response.Result.SSL.Status;
*/

$customHostnameID = $body->result->id;
$sslStatus = $body->result->ssl->status;
printf("API call to issue certificate returned with initial SSL status of %s (hostname ID=%s).".PHP_EOL,
	$sslStatus, $customHostnameID);

// lastly, we loop indefinitely until we see the hostname has been issued
// note: it may never issue if the customer fails to add the CNAME
$sleepTimeInSeconds = 20;
print(PHP_EOL."Polling on certificate status indefinitely (will sleep $sleepTimeInSeconds seconds between calls):".PHP_EOL);
while(1) {
	$timestamp = date("h:i:s");
	print("[$timestamp] Checking on certificate status of $customerHostname.. ");
	$certStatus = getCertStatus($adapter, $zoneID, $customHostnameID);
	print($certStatus.PHP_EOL);
	if ($certStatus == "active") {
		break;
	}
	sleep($sleepTimeInSeconds);
}

print(PHP_EOL."Certificate has been issued and is live on Cloudflare's edge:".PHP_EOL);
printCertDetailsFromEdge($customerHostname);


// fetch the certificate and print important details about it
function printCertDetailsFromEdge($hostname) {
	$g = stream_context_create (array("ssl" => array("capture_peer_cert" => true)));
	$r = stream_socket_client("ssl://$hostname:443", $errno, $errstr, 30, STREAM_CLIENT_CONNECT, $g);
	$cont = stream_context_get_params($r);
	$cert = openssl_x509_parse($cont["options"]["ssl"]["peer_certificate"]);

	print("Primary Certificate Details:\n");
	print(str_repeat("-", 70).PHP_EOL);
	printf("%-20s %-20s".PHP_EOL, "Serial Number", $cert['serialNumber']);
	printf("%-20s %-20s".PHP_EOL, "Signature Algorithm", $cert['signatureTypeSN']);
	printf("%-20s %-20s".PHP_EOL, "Issue Date", gmdate("Y-m-d\TH:i:s\Z", $cert['validFrom_time_t']));
	printf("%-20s %-20s".PHP_EOL, "Expiration Date", gmdate("Y-m-d\TH:i:s\Z", $cert['validTo_time_t']));
	printf("%-20s %-20s".PHP_EOL, "Common Name", $cert['subject']['CN']);
	printf("%-20s %-20s".PHP_EOL, "Subject Alt. Name(s)", $cert['extensions']['subjectAltName']);
	print(str_repeat("-", 70).PHP_EOL);
}

// get the current SSL status of a custom hostname
function getCertStatus($adapter, $zoneID, $hostnameID) {

	// commented out pending https://github.com/cloudflare/cloudflare-php/issues/50
	/*
	customHostnameResult, err := api.CustomHostname(zoneID, hostnameID)
	if err != nil {
		log.Fatal(err)
	}

	return customHostnameResult.SSL.Status
	*/

	$client = new GuzzleHttp\Client();
	$res = $client->request("GET", "https://api.cloudflare.com/client/v4/zones/$zoneID/custom_hostnames/$hostnameID", [
	    'headers' => [
	    	'X-Auth-Email' => getenv("CF_API_EMAIL"),
	    	'X-Auth-Key' => getenv("CF_API_KEY")
	    ]
	]);

	$responseCode = $res->getStatusCode();
	if ($responseCode != 200) {
		exit("Error getting certificate details for $customerHostname (API returned $responseCode).".PHP_EOL);
	}

	$bodyString = $res->getBody()->getContents();
	$body = json_decode($bodyString);

	return $body->result->ssl->status;
}

// generate a hostname for demo purposes (subdomain of input parameter)
function generateHostnameAndCheckResolution($customerDomain, $whitelabelHostname) {
	$dateFormat = date("Y-m-d-his");
	$t = date($dateFormat);
	$customerHostname = "ex" . $t . "." . $customerDomain;
	print("Acquiring certificate for $customerHostname.\n");

	// first we check to see if the customerHostname resolves to the parent zone
	if (!pointedTo($customerHostname, $whitelabelHostname)) {
		print("WARNING: " . $customerHostname . " does not resolve to same IP(s) as " . $whitelabelHostname.PHP_EOL);
		print("Validation will not complete and certificate will not issue until CNAME has been pointed.".PHP_EOL);
	}

	return $customerHostname;
}

// (very) hacky way to check if one hostname points to another
// assumes that the IP address resolution is identical and sorted (may very well be, haven't checked)
function pointedTo($source, $target) {
	$sourceIPs = gethostbynamel($source);
	natsort($sourceIPs);

	$targetIPs = gethostbynamel($target);
	natsort($targetIPs);

	return 0 == strcmp(array_shift($sourceIPs), array_Shift($targetIPs));
}
?>