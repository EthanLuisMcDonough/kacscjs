package handlers;

import com.typesafe.config.Config;
import models.User;
import play.Environment;
import play.api.OptionalSourceMapper;
import play.api.UsefulException;
import play.api.routing.Router;
import play.http.DefaultHttpErrorHandler;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;
import scala.Some;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static play.mvc.Controller.session;

public class ErrorHandler extends DefaultHttpErrorHandler {
    private Environment environment;
    private Provider<Router> routes;

    @Inject
    public ErrorHandler(Config config, Environment environment, OptionalSourceMapper sourceMapper,
                        Provider<Router> routes) {
        super(config, environment, sourceMapper, routes);
        this.environment = environment;
        this.routes = routes;
    }

    @Override
    protected CompletionStage<Result> onProdServerError(RequestHeader request, UsefulException exception) {
        return CompletableFuture.completedFuture(
                Results.internalServerError(views.html.error500.render(User.getFromSession(session()))));
    }

    @Override
    protected CompletionStage<Result> onNotFound(RequestHeader request, String message) {
        if (environment.isProd()) {
            return CompletableFuture
                    .completedFuture(Results.notFound(views.html.error404.render(User.getFromSession(session()))));
        } else {
            return CompletableFuture.completedFuture(Results.notFound(views.html.defaultpages.devNotFound
                    .render(request.method(), request.uri(), Some.apply(routes.get()))));
        }
    }
}
