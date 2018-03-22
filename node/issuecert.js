const cron = require('node-cron');
const fetch = require('node-fetch');

const config = {
  "apiKey": process.env.CF_API_KEY,
  "email": process.env.CF_API_EMAIL,
  "parentZoneName": process.env.CF_ZONE
}

const IssueCert = ({
  customerHostname,
  validationMethod,
  customOriginServer
}) => {

  // Set required headers for all Cloudflare API calls
  const hdrs = {
    "Content-Type": "application/json",
    "X-Auth-Email": config.email,
    "X-Auth-Key": config.apiKey
  };

  let zoneID = '';

  const customHostnamePayload = () => {

    // Set the customcustomHostnamePayload and validation method for provisioning your customer's cert
    let jsonPayload = {
      "hostname": customerHostname,
      "ssl": {
        "method": validationMethod,
        "type": "dv"
      }
    };

    // If specified in customOriginServer parameter, amend the jsoncustomcustomHostnamePayload to
    // include a custom origin server
    if (customOriginServer !== undefined) {
      Object.defineProperty(jsonPayload, "custom_origin_server", {
        value: customOriginServer
      })
    }
    return JSON.stringify(jsonPayload);
  };

  // Return a promise that returns the zoneID for your whitelabeled zone when fulfilled
  const validateZone = () => {
    return Promise.resolve(fetch('https://api.cloudflare.com/client/v4/zones', {
        method: 'GET',
        headers: hdrs
      })
      .then(res => res.json())
      .then(json => {
        let i = 0;
        while (i < json.result.length) {
          if (json.result[i].name === config.parentZoneName) {
            // Log and return the zoneID that corresponds with your whitelabeled zone name
            console.log(json.result[i].id);
            zoneID = json.result[i].id
            return zoneID;
            break;
          }
          i++;
        }
      }))
  };

  const requestCert = () => {
    return fetch(`https://api.cloudflare.com/client/v4/zones/${zoneID}/custom_hostnames`, {
        method: 'POST',
        body: customHostnamePayload(),
        headers: hdrs
      })
      .then(res => res.json())
      .then(body => {
        console.log(body);
        return (body.success).toString();
      })
  };

  const getCertStatus = () => {
    return fetch(`https://api.cloudflare.com/client/v4/zones/${zoneID}/custom_hostnames?hostname=${customerHostname}`, {
        method: 'GET',
        headers: hdrs
      })
      .then(res => res.json())
      .then(body => {
        return printCertDetailsFromEdge(body)
      })
  };

  const printCertDetailsFromEdge = (body) => {
    console.log(body);
    if (typeof body.result.ssl === 'string') {
      switch (body.result.ssl.status) {
        case 'pending_validation':
          console.log(`${customerHostname} still awaiting ${validationMethod} validation`)
          break;

        case 'active':
          console.log(`Certificate Provisioned for: ${customerHostname}`)
          runCron.destroy();
          break;

        default:
          console.log(`${customerHostname} status unknown`)
          break;
      }
    }
  };

  const runCron = cron.schedule('* * * * *', () => {
    return getCertStatus();
  })

  return validateZone()
    .then(() => {
      return requestCert()
    }).then(() => {
      runCron;
    })
}

module.exports = IssueCert;
