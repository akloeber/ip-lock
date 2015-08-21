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
 * THE SOFTWARE IS PROVIDED "AS IS
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ipLock;

/**
 * Created by Andreas Klöber on 08.07.15.
 */
public enum WorkerEnv {

    ID,

    SIGNAL_SERVER_PORT,

    TRY_LOCK,

    USE_LOCK,

    SKIP_UNLOCK,

    HALT_IN_MUTEX_AREA,

    SYNC_FILE_PATH,

    BREAKPOINT,

    BREAKPOINT_TIMEOUT_MS,

    WORKER_LOCK_TIMEOUT_MS,

    IP_LOCK_TIMEOUT_MS;

    public String getVarName() {
        return "IPL_" + name();
    }
}
