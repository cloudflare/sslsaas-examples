# sslsaas-node
Sample code that demonstrates how to issue SSL certificates for custom hostnames using Promises in Node.js via the `node-fetch` library.

## Installation

Requires Node version v5.0.0 or later
```
npm install
```
## Usage

To get started, open up `app.js` and modify the parameters for `issueCert()`:
```javascript
issueCert({
  customerHostname: 'store.nikitamarie.com',
  validationMethod: 'cname',
  customOriginServer: 'dedicated-sneaker-server.saasprovider.com' // Optional
})
```
**`customerHostname`**
The customer subdomain to which you want to issue a certificate

**`validation`**
The method you're using to verify customer's domain ownership (`'cname'` or `'http'`)

**`customOriginServer`**  _[Optional]_
The address for the custom origin server designated for this customer. If you choose to specify this param, there MUST be a valid DNS or LB record (or wildcard) in your Cloudflare account before a certificate will be issued.

### Set the environment variables and run the app:

To authenticate and run the code, just specify three environment variables and run `node app.js`:
```
CF_API_KEY="YOURAPIKEY" CF_API_EMAIL="YOUREMAIL" CF_ZONE="YOURMAINZONE" node app.js
```
> `CF_ZONE` is the DNS zone that's administering the service (e.g. saasprovider.com)

Once you start the server, a cron job makes calls every 60 seconds to Cloudflare's API until your customer's certificate status changes to 'active'.
