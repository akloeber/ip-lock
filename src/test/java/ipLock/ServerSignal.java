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
import java.util.List;

/**
 * Class ServerSignal
 *
 * @author Andreas Klöber
 */
public class ServerSignal {

    private final Integer receiverId;

    private final SignalCode code;

    public ServerSignal(Integer receiverId, SignalCode code) {
        this.receiverId = receiverId;
        this.code = code;
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add(getReceiverId().toString());
        parts.add(getCode().toString());

        return StringUtils.join(parts, ':');
    }

    public static ServerSignal valueOf(String data) {
        try {
            String[] fields = StringUtils.split(data, ':');

            Integer receiverId = Integer.valueOf(fields[0]);
            SignalCode code = SignalCode.valueOf(fields[1]);

            return new ServerSignal(receiverId, code);
        } catch (RuntimeException e) {
            String msg = String.format("message '%s' can not be parsed into server signal", data);
            throw new IllegalArgumentException(msg, e);
        }
    }

    public Integer getReceiverId() {
        return receiverId;
    }

    public SignalCode getCode() {
        return code;
    }
}
