// This can be copied into your project and tweaked as necessary. You'll need fastify installed as a dev dependency

import dotenv from "dotenv";

dotenv.config({ path: '.env.local' });

import { handleRequest, validMethods } from '../api/entrypoint.js';

import Fastify from 'fastify'

const fastify = Fastify({
  logger: false,
});

fastify.route({
    path: '/api/*',
    method: Object.keys(validMethods),
    handler: async (request) => {
        // This is somewhat hacky, but gives us an API-compatible harness that keeps its state...
        const encoder = new TextEncoder("utf-8");
        const body = encoder.encode(JSON.stringify(request.body)).buffer;

        const url = `http://${request.host}${request.url}`;
        const req = new Request(url, {
            headers: request.headers,
            body,
            method: request.method
        });

        const response = await handleRequest(req);

        // If you're using this with a web app, you will probably need
        // to accept CORS requests
        if (
            !response.headers.has('Access-Control-Allow-Origin')
            && request.headers.origin.startsWith('http://localhost')
        ) {
            response.headers.set('Access-Control-Allow-Headers', '*');
            response.headers.set('Access-Control-Allow-Origin', request.headers.origin);
        }

        return response;
    }
})

fastify.listen({ port: process.env.PORT ?? 3000 }, (err, address) => {
    if (err) throw err;
    console.log(`Listening on: ${address}`);
})

