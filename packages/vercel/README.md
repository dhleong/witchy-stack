witchy vercel
=============

This module provides helpers for building [vercel][vercel] functions in clojurescript.

## shadow-cljs target

Included as a `:vercel` build target for shadow-cljs. Use it like:

```clj
:builds {:api {:target :vercel
               :functions {:interactions [my.app.interactions/GET
                                          my.app.interactions/POST]

                           ; If the function accepts a single method,
                           ; you can pass it directly
                           :logs my.app.logs/GET}}}
```

## Routing

Note that, sadly, vercel assumes all functions should already exist on disk at deploy time, and doesn't handle dynamically built files in there. There are a couple workarounds:

### Use the entrypoint as a router

The `:vercel` build target generates an entrypoint.js that routes requests based on the keyword provided in `:functions` above. You can create a single `router.js` that is committed to your repo and references this generated entrypoint:

```clj
import {handleRequest} from './entrypoint.js';

export default handleRequest;
```

Then, in `vercel.json`:

```json
{
    "rewrites": [
        {
            "source": "/api/(.*)",
            "destination": "/api/router.js"
        }
    ]
}
```

### Manual routing

If you prefer that vercel build separate functions for each export, you'll have to do that manually. That might look like

```clj
// shadow-cljs.edn:
:builds {:api {:target :vercel
               :functions {:cljs-interactions [my.app.interactions/GET
                                               my.app.interactions/POST]}}}
```

```js
// api/interactions.js
export { GET, POST } from './cljs-interactions.js';
```

[vercel]: https://vercel.com
