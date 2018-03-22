# sslsaas-php
Sample code that demonstrates how to issue SSL certificates for custom hostnames
using Cloudflare's [PHP library](https://github.com/cloudflare/cloudflare-php).

## Installation

You need a working PHP environment with [composer](https://getcomposer.org/doc/00-intro.md) installed.

```
composer require cloudflare/sdk
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

You should also explicitly specify the zone in which the whitelabel hostname
resides as this is how the program checks to see that the auto-generated
"customer" hostname points in to your domain correctly (via CNAME).

```
export CUSTOMERDOMAIN=custer-domain.com
export WHITELABELZONE=yourzone.com
export WHITELABELHOST=whitelabel.yourzone.com
```

Then, to run, simply execute the program:
```
php issuecert.php
```

## Example Output

```
$ php issuecert.php 
Acquiring certificate for ex2018-03-19-043932.customer-domain.com
API call to issue certificate returned with initial SSL status of pending_validation (hostname ID=8d21ad27-a51c-4f41-98c7-9c142e48c2ba).

Polling on certificate status indefinitely (will sleep 20 seconds between calls):
[04:39:33] Checking on certificate status of ex2018-03-19-043932.customer-domain.com.. pending_validation
[04:39:54] Checking on certificate status of ex2018-03-19-043932.customer-domain.com.. pending_deployment
[04:40:14] Checking on certificate status of ex2018-03-19-043932.customer-domain.com.. active

Certificate has been issued and is live on Cloudflare's edge:
Primary Certificate Details:
----------------------------------------------------------------------
Serial Number        12940643149554446098646406236969752159
Signature Algorithm  ecdsa-with-SHA256   
Issue Date           2018-03-19T00:00:00Z
Expiration Date      2019-03-19T12:00:00Z
Common Name          ex2018-03-19-043932.customer-domain.com
Subject Alt. Name(s) DNS:ex2018-03-19-043932.customer-domain.com
----------------------------------------------------------------------
```
