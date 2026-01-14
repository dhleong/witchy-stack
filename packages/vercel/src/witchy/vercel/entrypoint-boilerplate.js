export async function handleRequest(request) {
  // compat with localdev mode vs prod mode: Conceivably could
  // compile out, but let's just be defensive
  const path = request.url.startsWith('/')
    ? request.url
    : new URL(request.url).pathname;
  const fetchHandlers = registry[path];

  if (fetchHandlers == null) {
    return new Response(null, { status: 404 });
  }

  const handlers = await fetchHandlers();
  const handler = handlers[request.method];

  if (handler == null || validMethods[request.method] == null) {
    // Handle OPTIONS requests for use as the API server to a web project:
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          Allow: Object.keys(handlers)
            .filter((method) => validMethods[request.method] != null)
            .join(", "),
        }
      });
    }

    // Unsupported method
    return new Response(null, { status: 405 });
  }
  return handler(request);
}
