[![Shippable](https://img.shields.io/shippable/54d119db5ab6cc13528ab183.svg)](https://app.shippable.com/projects/55d775b21895ca44740ee432)


# [IpLock](https://github.com/akloeber/ip-lock)

> An inter-process lock for synchronization of multiple JVM-based Java processes running on the same machine.


## Overview

* [Features](#features)
* [Installation](#installation)
* [Usage](#usage)
* [Test](#test)
* [API Documentation](#api-documentation)
* [License](#license)


## Features

*   Acquire lock with optional timeout (__blocking mode__)
*   Try to acquire lock (__non-blocking mode__)
*   Explicitly release lock
*   Automatically releases lock when process finishes, crashes or is killed
*   Comprehensive test suite


## Installation

The library itself does not have any further dependencies apart from JRE 7 or higher, so it can be installed by just
adding `IpLock`-class to your project.


## Usage

This is an example for making a process exclusive on a machine:

```java
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


## Test

This libary has been tested on the following platforms:

*   Mac OS X 10.10.5, Oracle JRE 1.7.0_79-b15
*   Ubuntu 12.04 via [Shippable](https://app.shippable.com/projects/55d775b21895ca44740ee432):
    * OpenJDK 7
    * OpenJDK 8
    * Oracle JDK 7
    * Oracle JDK 8

For testing it on your platform execute:

```bash
mvn test
```


## API Documentation

#### `public class IpLock`

An inter-process lock for synchronization of multiple JVM based processes running on the same machine.
<p/>
If the process that owns the lock finishes without releasing it, the lock is released automatically. This is also valid if the process owning the lock is destroyed or killed. <p/> The synchronization is implemented based on [`java.nio.channels.FileLock`](http://docs.oracle.com/javase/7/docs/api/java/nio/channels/FileLock.html).
<p/>
This class is thread-safe: multiple threads can share a single {@link IpLock} object without the need for external synchronization.

 * **Author:** Andreas Klöber
 * **See also:** [`java.nio.channels.FileLock`](http://docs.oracle.com/javase/7/docs/api/java/nio/channels/FileLock.html)

##### `public IpLock(final File syncFile)`

Create a new lock object that uses the given file for synchronization. The file will be created if it does not exist.

 * **Parameters:** `syncFile` — the file to be used for synchronization

##### `public IpLock(final String syncFilePath)`

Create a new lock object that uses the given file for synchronization. The file will be created if it does not exist.

 * **Parameters:** `syncFilePath` — path to the file to be used for synchronization

###### `public void lock() throws IOException`

Acquires the lock in a blocking way.
<p/>
Only one process can acquire the lock at the same time. This method waits indefinitely until the lock could be acquired.

 * **Exceptions:** `IOException` — if the synchronization file could not be created, e.g. because of missing write permissions in target folder

###### `public boolean lock(long timeout, long tryLockInterval, TimeUnit timeUnit) throws IOException, InterruptedException`

Acquires the lock in a blocking way.
<p/>
Only one process can acquire the lock at the same time. In addition to `lock()` this method also allows configuration of a timeout.
<p/>
As the underlying `FileLock` object does not provide a way to cancel a lock request in case of a timeout, this method periodically tries to get the lock until this is successful or the timeout limit is reached. 

 * **Parameters:**
   * `timeout` — the timeout limit
   * `tryLockInterval` — the time interval for trying locks
   * `timeUnit` — the `TimeUnit` for both <tt>timeout</tt> and <tt>tryLockInterval</tt> parameters
 * **Returns:** <code>true</code> if the lock could be required; <code>false</code> if there was a timeout
 * **Exceptions:**
   * `IOException` — if the synchronization file could not be created (e.g. because of missing write permissionsin in target folder) or if some other I/O error occurs on the underlying `FileLock`
   * `InterruptedException` — if the current thread is interrupted while waiting

###### `public boolean tryLock() throws IOException`

Tries to acquire the lock and returns immediately.
<p/>
Only one process can acquire the lock at the same time. The result determines whether the lock could be acquired or not. 

 * **Returns:** <code>true</code> if the lock could be required; <code>false</code> if there was a timeout
 * **Exceptions:** `IOException` — if the synchronization file could not be created (e.g. because of missing write permissions in target folder) or if some other I/O error occurs on the underlying `FileLock`

###### `public void unlock() throws IOException`

Releases the lock.
<p/>
If the lock has not been acquired before, this method returns immediately.

 * **Exceptions:** `IOException` — if some other I/O error occurs on the underlying `FileLock`

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

