const server = require('http').Server();
const port = process.env.PORT || 3000;

const issueCert = require('./issuecert');

issueCert({
  customerHostname: '',
  validationMethod: '',
  customOriginServer: ''
})

server.listen(port);
console.log('\nϟϟϟ Serving on port ' + port + ' ϟϟϟ\n');
