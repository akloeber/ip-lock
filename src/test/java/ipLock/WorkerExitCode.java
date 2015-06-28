package ipLock;

public enum WorkerExitCode {

    SUCCESS(0),

    CONCURRENT_ACCESS_ERROR(1),

    TRY_LOCK_FAILED(2);

    private int code;

    WorkerExitCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
