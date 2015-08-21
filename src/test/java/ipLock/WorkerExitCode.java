/*
 * Copyright (c) 2015 Andreas Klöber
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ipLock;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum WorkerExitCode {

    SUCCESS(0),

    TRY_LOCK_FAILED(1),

    BREAKPOINT_TIMEOUT(2),

    WORKER_LOCK_TIMEOUT(3),

    HALT_IN_MUTEX_AREA(4);

    private static Map<Integer, WorkerExitCode> workerExitCodeMap = new HashMap<>();

    static {
        for (WorkerExitCode wec : EnumSet.allOf(WorkerExitCode.class)) {
            workerExitCodeMap.put(wec.getCode(), wec);
        }
    }

    private int code;

    WorkerExitCode(int code) {
        this.code = code;
    }

    public static WorkerExitCode valueOf(int code) {
        return workerExitCodeMap.get(code);
    }

    public int getCode() {
        return code;
    }
}
