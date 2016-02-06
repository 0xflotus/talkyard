var _ = require('lodash');
var minimist = require('minimist');
var logAndDie = require('./log-and-die.js');
var logUnusual = logAndDie.logUnusual, die = logAndDie.die, dieIf = logAndDie.dieIf;
var logWarning = logAndDie.logWarning, logMessage = logAndDie.logMessage;

var settings = {
  host: 'localhost',
  testLocalHostnamePrefix: 'e2e-test--',
  testEmailAddressPrefix: 'e2e-test--',
};

/*
interface settings {
  secure: boolean;
  host: string;
  scheme: string;
  mainSiteOrigin: string;
  newSiteDomain: string;
  e2eTestPassword: string;
  testLocalHostnamePrefix: string;
  testEmailAddressPrefix: string;
  gmailEmail: string;
  gmailPassword: string;
  facebookAdminPassword: string;
  facebookAdminEmail: string;
  facebookUserPassword: string;
  facebookUserEmail: string;
}
*/


// ---- Analyze arguments

var args = minimist(process.argv.slice(2));
_.extend(settings, args);

settings.scheme = settings.secure ? 'https' : 'http';
settings.mainSiteOrigin = settings.scheme + '://' + settings.host;
settings.newSiteDomain = settings.newSiteDomain || settings.host;
settings.waitforTimeout = args.noTimeout || args.nt ? 99999999 : 10*1000;

if (settings.password) {
  settings.e2eTestPassword = settings.password;
  delete settings.password;
}


// ---- Setup secrets

var secretsPath = args.secretsPath;
if (secretsPath) {
  var fs = require('fs');
  var fileText = fs.readFileSync(secretsPath, { encoding: 'utf8' });
  try {
    var secrets = JSON.parse(fileText);
    settings = _.extend({}, secrets, settings); // command line stuff overrides file
    if (!settings.e2eTestPassword) logWarning(
        "No password command line option or e2eTestPassword in " + secretsPath);
    if (!settings.gmailEmail) logWarning("No gmailEmail in " + secretsPath);
    if (!settings.gmailPassword) logWarning("No gmailPassword in " + secretsPath);
    if (!settings.facebookAdminPassword) logWarning("No facebookAdminPassword in " + secretsPath);
    if (!settings.facebookAdminEmail) logWarning("No facebookAdminEmail in " + secretsPath);
    if (!settings.facebookUserPassword) logWarning("No facebookUserPassword in " + secretsPath);
    if (!settings.facebookUserEmail) logWarning("No facebookUserEmail in " + secretsPath);
  }
  catch (error) {
    die("Error parsing secret file: " + error);
  }
}

/*
console.log("==================================================");
console.log("~~~~~~ Test settings:");
console.log("host: " + settings.host);
console.log("secure: " + settings.secure);
console.log('derived origin: ' + settings.mainSiteOrigin);
console.log("~~~~~~ Secrets:");
console.log("e2eTestPassword: " + (settings.e2eTestPassword ? "(yes)" : "undefined"));
console.log("gmailEmail: " + settings.gmailEmail);
console.log("facebookAdminEmail: " + settings.facebookAdminEmail);
console.log("facebookUserEmail: " + settings.facebookUserEmail);
console.log("~~~~~~ Extra magic:");
if (settings.pauseForeverAfterTest) {
  console.log("You said " + unusualColor("--pauseForeverAfterTest") +
      ", so I will pause forever after the first test.");
}
if (settings.noTimeout) {
  console.log("You said " + unusualColor("--noTimeout") +
      ", so I might wait forever for something in the browser.");
}
console.log("==================================================");
*/


module.exports = settings;
