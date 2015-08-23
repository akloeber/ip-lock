[![Shippable](https://img.shields.io/shippable/54d119db5ab6cc13528ab183.svg)](https://app.shippable.com/projects/55d775b21895ca44740ee432)

# IpLock

An inter-process lock for synchronization of multiple JVM based Java processes running on the same machine.

## Features
*   Acquire lock with optional timeout (blocking)
*   Try to acquire lock (non-blocking)
*   Explicitely release lock
*   Automatically release lock when process finishes, crashes or is killed
*   Comprehensive test suite

## Installation
The library itself does not have any further dependencies apart from JRE 7 or higher, so it can be installed by just
adding `IpLock`-class to your project.

## Usage
This is an example for making a process exclusive on a machine:
```
public class ProcessSingleton {

    private static final IpLock LOCK = new IpLock("/tmp/ProcessSingleton.lock");

    public static void main(String[] args) throws Exception {
        if (!LOCK.tryLock()) {
            throw new IllegalStateException(
                "Another instance of this process is already running");
        }

        try {
            // do your stuff instead
            Thread.sleep(5000);
        } finally {
            LOCK.unlock();
        }
    }
}
```
TODO: provide singleton exmaple on process level

## Test
This libary has been tested on the following platforms:

*   Mac OS X 10.10.5, Oracle JRE 1.7.0_79-b15
*   Ubuntu 12.04 via Shippable:
    * OpenJDK 7
    * OpenJDK 8
    * Oracle JDK 7
    * Oracle JDK 8

For testing it on your platform execute:
```
mvn test
```

## License

Copyright (c) 2015 Andreas Klöber

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

