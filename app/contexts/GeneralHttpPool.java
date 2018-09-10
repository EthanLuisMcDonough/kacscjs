package contexts;

import play.libs.concurrent.CustomExecutionContext;
import akka.actor.ActorSystem;
import javax.inject.Inject;

public class GeneralHttpPool extends CustomExecutionContext {
    @Inject
    public GeneralHttpPool(ActorSystem system) {
        super(system, "gen-http-pool");
    }
}
