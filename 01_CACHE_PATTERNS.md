# Cache patterns

Cache patterns implemented with Hazelcast are described, at high level, in the blog [A Hitchhiker’s Guide to Caching Patterns](https://hazelcast.com/blog/a-hitchhikers-guide-to-caching-patterns/). We'll focus now on how to implement the three most basic patterns:

- **Read-Through**:  on a cache miss, the cache manager fetches the data from the underlying data store
- **Write-Through**: on a cache write, the cache manager stores synchronously the data to the underlying data store
- **Write-Behind**: on a cache write, the cache manager buffers the writes and asynchronously writes to the underlying data store.