# sslsaas-node
Sample code that demonstrates how to issue SSL certificates for custom hostnames using Promises in Node.js via the `node-fetch` library.

## Installation

Requires Node version v5.0.0 or later
```
cd sslsaas-examples/node && npm install
```
## Usage
This code can run as a standalone application or be mounted onto an existing Node.js server. To get started, open up `app.js` and modify the parameters for `issueCert()`:
```javascript

// Example
issueCert({
  customerHostname: 'store.shoesbynikita.com',
  validationMethod: 'http',
  customOriginServer: 'dedicated-server-1.saasprovider.com' // Optional
})
```
**`customerHostname`**
The customer subdomain to which you want to issue a certificate

**`validationMethod`**
The method you're using to verify customer's domain ownership (`'cname'` or `'http'`)

**`customOriginServer`**
_Optional_: The address for the custom origin server designated for this customer. If you choose to specify this param, there MUST be a valid DNS or LB record (wildcards are accepted) in your Cloudflare account before a certificate will be issued.

### Set the environment variables and run the app:

To authenticate and run the code, these three environment variables, and append `node app.js`:
```
CF_API_KEY="YOURAPIKEY" CF_API_EMAIL="YOUREMAIL" CF_ZONE="YOURMAINZONE" node app.js
```
> `CF_ZONE` is the DNS zone that's administering the service (e.g. saasprovider.com)

Once you start the server, a cron makes calls every 15 seconds against Cloudflare's API until the status of your customer's certificate changes to `active`.
