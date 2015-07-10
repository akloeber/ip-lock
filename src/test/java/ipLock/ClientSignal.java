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

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ClientSignal {

    private final Integer senderId;

    private final SignalCode code;

    private final String[] params;

    public ClientSignal(int senderId, SignalCode code, String... params) {
        this.senderId = senderId;
        this.code = code;
        this.params = params;
    }

    public static ClientSignal valueOf(String data) {
        try {
            String[] fields = StringUtils.split(data, ':');

            Integer senderId = Integer.valueOf(fields[0]);
            SignalCode code = SignalCode.valueOf(fields[1]);
            String[] params = Arrays.copyOfRange(fields, 2, fields.length);

            return new ClientSignal(senderId, code, params);
        } catch (RuntimeException e) {
            String msg = String.format("message '%s' can not be parsed into client signal", data);
            throw new IllegalArgumentException(msg, e);
        }
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add(getSenderId().toString());
        parts.add(getCode().toString());
        Collections.addAll(parts, params);

        return StringUtils.join(parts, ':');
    }

    public SignalCode getCode() {
        return code;
    }

    public String[] getParams() {
        return params;
    }

    public Integer getSenderId() {
        return senderId;
    }
}
