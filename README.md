## Vert.x SQL Client with Mutiny demo

### List products

```
> curl -X POST -H "Content-Type: application/json" -d '{"name":"Spoon","price":1.0}' http://localhost:8080/products

or

> http localhost:8080/products name=Spoon price=1.0

```

### Create product

```
curl http://localhost:8080/products

or

> http localhost:8080/products
```

### Get product

```
curl http://localhost:8080/products/1

or

> http localhost:8080/products/1
```
