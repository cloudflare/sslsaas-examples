# sslsaas-go
Sample code that demonstrates how to issue SSL certificates for custom hostnames
using Cloudflare's [golang library](https://github.com/cloudflare/cloudflare-go).

## Installation

You need a working Go environment with Cloudflare's go library installed.

```
go get github.com/cloudflare/cloudflare-go
```

## Usage

You'll then want to set environment variables with your Cloudflare username and
API key:

```
export CF_API_KEY=YOURKEY
export CF_API_EMAIL=YOUREMAIL
```

Next, because this code generates hostnames based on the current time, you'll
need to provide the (sub)domain on which these hosts should sit. To facilitate
testing, it is recommended that you configure your "customer's" authoritative
DNS provider to do wildcard resolution to your Managed CNAME zone.

For example, *.customer-domain.com should resolve to whitelabel.yourzone.com.

You should also explicitly specifiy the zone in which the whitelabel hostname
resides as this is how the program checks to see that the auto-generated
"customer" hostname points in to your domain correctly (via CNAME).

```
export CUSTOMERDOMAIN=customer-domain.com
export WHITELABELZONE=yourzone.com
export WHITELABELHOST=whitelabel.yourzone.com
```

Then, to run, simply execute the program:
```
go run issuecert.go
```

## Example Output

```
$ go run issuecert.go
Acquiring certificate for ex2017-10-15-094853.customer-domain.com.
API call to issue certificate returned with initial SSL status of pending_validation (hostname ID=2587fd4e-215a-4b3c-82ff-ab07ac1e08fd).

Polling on certificate status indefinitely (will sleep between calls):
[20:48:57] Checking on certificate status of ex2017-10-15-094853.customer-domain.com.. pending_validation
[20:49:17] Checking on certificate status of ex2017-10-15-094853.customer-domain.com.. pending_validation
[20:49:37] Checking on certificate status of ex2017-10-15-094853.customer-domain.com.. pending_deployment
[20:50:57] Checking on certificate status of ex2017-10-15-094853.customer-domain.com.. active

Certificate has been issued and is live on Cloudflare's edge:
Certificate Details:
----------------------------------------------------------------------
Serial Number        13059011992128372826617107584774329703
Signature Algorithm  ECDSA-SHA256        
Issue Date           2017-10-15 00:00:00 +0000 UTC
Expiration Date      2018-10-15 12:00:00 +0000 UTC
Common Name          ex2017-10-15-094853.customer-domain.com
Subject Alt. Name(s) [ex2017-10-15-094853.customer-domain.com]
----------------------------------------------------------------------
```
