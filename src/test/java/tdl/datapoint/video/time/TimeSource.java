package tdl.datapoint.video.time;

import java.util.concurrent.TimeUnit;

public interface TimeSource {

    long currentTimeNano();

    void wakeUpAt(long timestamp, TimeUnit timeUnit) throws InterruptedException;
}
