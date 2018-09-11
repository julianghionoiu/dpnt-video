package tdl.datapoint.video.time;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class SystemTimeSource implements TimeSource {

    @Override
    public long currentTimeNano() {
        return System.nanoTime();
    }


    @Override
    public void wakeUpAt(long timestamp, TimeUnit timeUnit) throws InterruptedException {
        long currentTimestampNano = currentTimeNano();
        long targetTimestampNano = timeUnit.toNanos(timestamp);

        long timeToSleepMillis = TimeUnit.NANOSECONDS
                .toMillis(targetTimestampNano - currentTimestampNano);

        if (timeToSleepMillis > 1) {
            log.debug("Sleep for: {} millis", timeToSleepMillis);
            Thread.sleep(timeToSleepMillis);
        }
    }
}
