package req;

import org.junit.Test;
import play.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpinOffTest {
    @Test
    public void iterTest() throws Exception {
        Iterator<ArrayListExceptions<Long>> iter = new SpinOffIter(5234130249875456l);
        List<Long> ids = new ArrayList<Long>();
        while (iter.hasNext()) {
            ArrayListExceptions<Long> batch = iter.next();
            if (batch.successful()) {
                ids.addAll(batch);
            } else {
                throw batch.getExceptions().get(0);
            }
        }

        String idString = "";
        int i = 0;
        for (long id : ids) {
            idString += (i++ == 0 ? "" : ", ") + String.valueOf(id);
        }
        Logger.info(idString);
    }
}
