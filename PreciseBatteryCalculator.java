    private static final int MIN_SAMPLES = 5;
    private static final int MAX_SAMPLES = 50;
    private static final int CALIBRATION_MIN_PERCENT = 15;
    private static final int CALIBRATION_MAX_PERCENT = 95;
    private static final double MAX_DEVIATION_PERCENT = 2.0;
    private static final long SAMPLE_RETENTION_MS = 7L * 24 * 60 * 60 * 1000; // 7 days
    private static final long MIN_SAMPLE_INTERVAL_MS = 5L * 60 * 1000; // 5 minutes

    private List<CapacitySample> samples = new ArrayList<>();
    private double smoothedCapacity = -1;
    private int lastSystemPercent = -1;
    private long lastSampleTimestamp = 0;
