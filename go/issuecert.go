package main

import (
	"bytes"
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"log"
	"net"
	"os"
	"time"

	"github.com/cloudflare/cloudflare-go"
	"strings"
)

func main() {
	// this is the Managed CNAME zone that will serve as the container for the custom hostnames
	parentZoneName := os.Getenv("WHITELABELZONE")

	// this is the hostname that's provided to customers as part of your onboarding instructions
	// this should be the same hostname (or one that CNAMEs to) the proxy fallback host provided
	// to your Cloudflare SE during onboarding of your Managed CNAME zone
	whitelabelHostname := os.Getenv("WHITELABELHOST")

	// below we generate the customer's vanity/custom customerHostname based on current UTC time
	// this generated customerHostname (wildcard) resolves to whiteLabelHostname
	customerDomain := os.Getenv("CUSTOMERDOMAIN")
	customerHostname := generateHostnameAndCheckResolution(customerDomain, whitelabelHostname)

	// next, we connect to the Cloudflare API, using the API key and email from the shell environment
	api, err := cloudflare.New(os.Getenv("CF_API_KEY"), os.Getenv("CF_API_EMAIL"))
	if err != nil {
		log.Fatal(err)
	}

	// after connecting, we look up the zoneID so it can be used for the custom_hostname API call
	zoneID, err := api.ZoneIDByName(parentZoneName)
	if err != nil {
		log.Fatal(err)
	}

	// and then we make the API call to issue a certificate using HTTP validation and print the call result
	customHostnamePayload := cloudflare.CustomHostname{Hostname: customerHostname, SSL: cloudflare.CustomHostnameSSL{Method: "http", Type: "dv"}}
	response, err := api.CreateCustomHostname(zoneID, customHostnamePayload)
	if err != nil {
		log.Fatal(err)
	}
	customHostnameID := response.Result.ID
	fmt.Printf("API call to issue certificate returned with initial SSL status of %s (hostname ID=%s).\n",
		response.Result.SSL.Status, response.Result.ID)

	// lastly, we loop indefinitely until we see the hostname has been issued
	// note: it may never issue if the customer fails to add the CNAME
	const sleepTimeInSeconds = 20 * time.Second
	fmt.Println("\nPolling on certificate status indefinitely (will sleep between calls):")
	for {
		timestamp := time.Now().Format("15:04:05")
		fmt.Print("[" + timestamp + "] Checking on certificate status of " + customerHostname + ".. ")
		certStatus := getCertStatus(api, zoneID, customHostnameID)
		fmt.Println(certStatus)
		if certStatus == "active" {
			break
		}
		time.Sleep(sleepTimeInSeconds)
	}

	fmt.Println("\nCertificate has been issued and is live on Cloudflare's edge:")
	printCertDetailsFromEdge(customerHostname)
}

func printCertDetailsFromEdge(hostname string) {
	conn, err := tls.Dial("tcp", hostname+":443", &tls.Config{
		InsecureSkipVerify: false,
	})
	if err != nil {
		fmt.Println(err)
	}

	cert := conn.ConnectionState().PeerCertificates[0]

	fmt.Println("Certificate Details:")
	fmt.Println(strings.Repeat("-", 70))
	fmt.Printf("%-20s %-20s\n", "Serial Number", cert.SerialNumber)
	fmt.Printf("%-20s %-20s\n", "Signature Algorithm", x509.SignatureAlgorithm(cert.SignatureAlgorithm))
	fmt.Printf("%-20s %-20s\n", "Issue Date", cert.NotBefore)
	fmt.Printf("%-20s %-20s\n", "Expiration Date", cert.NotAfter)
	fmt.Printf("%-20s %-20s\n", "Common Name", cert.Subject.CommonName)
	fmt.Printf("%-20s %-20s\n", "Subject Alt. Name(s)", cert.DNSNames)
	fmt.Println(strings.Repeat("-", 70))
}

// get the current SSL status of a custom hostname
func getCertStatus(api *cloudflare.API, zoneID string, hostnameID string) string {
	customHostnameResult, err := api.CustomHostname(zoneID, hostnameID)
	if err != nil {
		log.Fatal(err)
	}

	return customHostnameResult.SSL.Status
}

// generate a hostname for demo purposes (subdomain of input parameter)
func generateHostnameAndCheckResolution(customerDomain string, whitelabelHostname string) string {
	dateFormat := "2006-01-02-150405"
	t := time.Now().UTC().Format(dateFormat)
	customerHostname := "ex" + t + "." + customerDomain
	fmt.Println("Acquiring certificate for " + customerHostname + ".")

	// first we check to see if the customerHostname resolves to the parent zone
	if !pointedTo(customerHostname, whitelabelHostname) {
		fmt.Println("WARNING: " + customerHostname + " does not resolve to same IP(s) as " + whitelabelHostname)
		fmt.Println("Validation will not complete and certificate will not issue until CNAME has been pointed.")
	}

	return customerHostname
}

// (very) hacky way to check if one hostname points to another
// assumes that the IP address resolution is identical and sorted (may very well be, haven't checked)
func pointedTo(source string, target string) bool {
	sourceIPs, err := net.LookupIP(source)
	if err != nil {
		log.Fatal(err)
	}
	targetIPs, err := net.LookupIP(target)
	if err != nil {
		log.Fatal(err)
	}

	return 0 == bytes.Compare(sourceIPs[0], targetIPs[0])
}
