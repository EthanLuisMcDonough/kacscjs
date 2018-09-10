package contexts;

import play.libs.concurrent.CustomExecutionContext;
import akka.actor.ActorSystem;
import javax.inject.Inject;

public class DBContext extends CustomExecutionContext {
    @Inject
    public DBContext(ActorSystem system) {
        super(system, "db-pool");
    }
}
