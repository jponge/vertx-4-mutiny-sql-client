import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.http.HttpServer;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.RoutingContext;
import io.vertx.mutiny.ext.web.handler.BodyHandler;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static java.util.Objects.requireNonNull;

public class ApiVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(ApiVerticle.class);

  private PgPool pgPool;

  @Override
  public Uni<Void> asyncStart() {

    PgConnectOptions connectOptions = new PgConnectOptions()
      .setPort(config().getInteger("pgPort", 5432))
      .setHost("localhost")
      .setDatabase("postgres")
      .setUser("postgres")
      .setPassword("vertx-in-action");

    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

    pgPool = PgPool.pool(vertx, connectOptions, poolOptions);

    Router router = Router.router(vertx);

    BodyHandler bodyHandler = BodyHandler.create();
    router.post().handler(bodyHandler::handle);

    router.get("/products").respond(rc -> listProducts());
    router.get("/products/:id").respond(this::getProduct);
    router.post("/products").respond(this::createProduct);

    Uni<HttpServer> startHttpServer = vertx.createHttpServer()
      .requestHandler(router::handle)
      .listen(8080)
      .onItem().invoke(() -> logger.info("HTTP server listening on port 8080"));

    return startHttpServer.replaceWithVoid();
  }

  private Uni<JsonArray> listProducts() {
    logger.info("listProducts");

    Uni<RowSet<Row>> execute = pgPool.query("SELECT * FROM Product")
      .execute();
    return execute
      .onItem().transform(rowset -> {
        JsonArray data = new JsonArray();
        for (Row row : rowset) {
          data.add(row.toJson());
        }
        return data;
      })
      .onFailure().recoverWithItem(new JsonArray());
  }

  private Uni<JsonObject> createProduct(RoutingContext rc) {
    logger.info("createProduct");

    JsonObject json = rc.getBodyAsJson();
    String name;
    BigDecimal price;

    try {
      requireNonNull(json, "The incoming JSON document cannot be null");
      name = requireNonNull(json.getString("name"), "The product name cannot be null");
      price = new BigDecimal(json.getString("price"));
    } catch (Throwable err) {
      logger.error("Could not extract values", err);
      return Uni.createFrom().failure(err);
    }

    return pgPool.preparedQuery("INSERT INTO Product(name, price) VALUES ($1, $2) RETURNING id")
      .execute(Tuple.of(name, price))
      .onItem().transform(rowSet -> {
        Row row = rowSet.iterator().next();
        return new JsonObject()
          .put("id", row.getInteger("id"))
          .put("name", name)
          .put("price", price);
      })
      .onFailure().invoke(err -> logger.error("Woops", err));
  }

  private Uni<JsonObject> getProduct(RoutingContext rc) {
    logger.info("getProduct");
    Long id = Long.valueOf(rc.pathParam("id"));

    return pgPool
      .preparedQuery("SELECT * FROM Product WHERE id=$1")
      .execute(Tuple.of(id))
      .onItem()
      .transform(rows -> {
        RowIterator<Row> iterator = rows.iterator();
        if (iterator.hasNext()) {
          return iterator.next().toJson();
        } else {
          return new JsonObject();
        }
      })
      .onFailure().invoke(err -> logger.error("Woops", err));
  }

}

