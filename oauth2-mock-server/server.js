import {OAuth2Server} from 'oauth2-mock-server';
import {writeFileSync} from 'fs';
// ...or in CommonJS style:
// const { OAuth2Server } = require('oauth2-mock-server');

let server = new OAuth2Server();

// Generate a new RSA key and add it to the keystore
await server.issuer.keys.generate('RS256');

// Start the server
await server.start(8080, 'localhost');
let tr = {
    payload: {
        preferred_username: 'test',
    }
}
let token = await server.issuer.buildToken({
    scopesOrTransform: (req, tr) => {
        tr.preferred_username = 'test';
        tr.sub = 'test';
    },
    expiresIn: 30 * 24 * 2600
});

console.log('OpenId Config URL:', `${server.issuer.url}/.well-known/openid-configuration`)
console.log('Token:', token);
writeFileSync('http-client.private.env.json', JSON.stringify({
    "dev": {
        "baseUrl": "http://127.0.0.1:7070",
        "tenantHost": "127.0.0.1",
        "authToken": `Bearer ${token}`,
    }

}, null, 2));
