# sslsaas-java
Sample code that demonstrates how to issue SSL certificates for custom hostnames
using Cloudflare's [API](https://api.cloudflare.com/#custom-hostname-for-a-zone-create-custom-hostname). 

## Installation

You need a working Java (JDK 1.8 or higher) environment with Maven [3.5.3](https://maven.apache.org/download.cgi)
installed.

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

You should also explicitly specify the zone in which the whitelabel hostname
resides as this is how the program checks to see that the auto-generated
"customer" hostname points in to your domain correctly (via CNAME).

```
export CUSTOMERDOMAIN=customer-domain.com
export WHITELABELZONE=yourzone.com
export WHITELABELHOST=whitelabel.yourzone.com
```

```
mvn -Dmaven.test.skip=true clean package exec:java
```

## Example Output

```
...
Acquiring certificate for ex2018-03-22-012809.customer-domain.com.

API call to issue certificate returned with initial SSL status of pending_validation (hostname ID=617ed760-b5e1-4c45-83de-cfeda129f6a9).

Polling on certificate status indefinitely (will sleep 20 seconds between calls):
[ 18:28:30 ] Checking on certificate status of ex2018-03-22-012809.customer-domain.com.. pending_validation
[ 18:28:51 ] Checking on certificate status of ex2018-03-22-012809.customer-domain.com.. pending_deployment
[ 18:29:11 ] Checking on certificate status of ex2018-03-22-012809.customer-domain.com.. active

Certificate has been issued and is live on Cloudflare's edge:
Certificate Details:
----------------------------------------------------------------------
Serial Number        6307796265788909657855434244161946428
Signature Algorithm  SHA256withECDSA     
Issue Date           Wed Mar 21 17:00:00 PDT 2018
Expiration Date      Fri Mar 22 05:00:00 PDT 2019
Common Name          ex2018-03-22-012809.customer-domain.com
Subject Alt. Name(s) ex2018-03-22-012809.customer-domain.com
----------------------------------------------------------------------
...
```