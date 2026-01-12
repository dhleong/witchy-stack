// This can be copied into your project and tweaked as necessary. You'll need fastify installed as a dev dependency

import dotenv from "dotenv";

dotenv.config({ path: '.env.local' });

import { handleRequest } from '../api/entrypoint.js';

import Fastify from 'fastify'

const fastify = Fastify({
  logger: false,
});

fastify.route({
    path: '/api/*',
    method: ['DELETE', 'GET', 'HEAD', 'PATCH', 'POST', 'PUT', 'OPTIONS'],
    bodyLimit: 0,
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

        return handleRequest(req);
    }
})

fastify.listen({ port: process.env.PORT ?? 3000 }, (err, address) => {
    if (err) throw err;
    console.log(`Listening on: ${address}`);
})

