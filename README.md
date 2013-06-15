# Snowflake

Snowflake is a network service for generating unique ID numbers at high scale with some simple guarantees.

## Motivation

As we at Twitter move away from Mysql towards Cassandra, we've needed a new way to generate id numbers. There is no sequential id generation facility in Cassandra, nor should there be.

## Requirements

### Performance
 * minimum 10k ids per second per process
 * response rate 2ms (plus network latency)

### Uncoordinated

For high availability within and across data centers, machines generating ids should not have to coordinate with each other.

### (Roughly) Time Ordered

We have a number of API resources that assume an ordering (they let you look things up "since this id").

However, as a result of a large number of asynchronous operations, we already don't guarantee in-order delivery.

We can guarantee, however, that the id numbers will be k-sorted (references: http://portal.acm.org/citation.cfm?id=70413.70419 and http://portal.acm.org/citation.cfm?id=110778.110783) within a reasonable bound (we're promising 1s, but shooting for 10's of ms).

### Directly Sortable

The ids should be sortable without loading the full objects that the represent. This sorting should be the above ordering.

### Compact

There are many otherwise reasonable solutions to this problem that require 128bit numbers. For various reasons, we need to keep our ids under 64bits.

### Highly Available

The id generation scheme should be at least as available as our related services (like our storage services).

##  Solution
* Thrift Server written in Scala
* id is composed of:
  * time - 41 bits (millisecond precision w/ a custom epoch gives us 69 years)
  * configured machine id - 10 bits - gives us up to 1024 machines
  * sequence number - 12 bits - rolls over every 4096 per machine (with protection to avoid rollover in the same ms)

### System Clock Dependency

You should use NTP to keep your system clock accurate.  Snowflake protects from non-monotonic clocks, i.e. clocks that run backwards.  If your clock is running fast and NTP tells it to repeat a few milliseconds, snowflake will refuse to generate ids until a time that is after the last time we generated an id. Even better, run in a mode where ntp won't move the clock backwards. See http://wiki.dovecot.org/TimeMovedBackwards#Time_synchronization for tips on how to do this.

## Building

[![Build Status](https://secure.travis-ci.org/twitter/snowflake.png)](http://travis-ci.org/twitter/snowflake)

### Download the deb tested on Ubuntu 13.04, EC2

[megamsnowflake-0.1.0.deb](http://s3.com) 

* work under progress

### Chef cookbook for megamsnowflake deb tested on Ubuntu 13.04, EC2

* work under progress

[megam snowflake](http://github.com/indykish/chef-repo/tree/master/cookbooks/megam_snowflake) - work under progress

### Requirements

If you cloned [megam snowflake](https://github.com/indykish/snowflake)

> 
[Zookeeper 3.4.5 +](http://http://zookeeper.apache.org/)
[OpenJDK 7.0](http://openjdk.java.net/install/index.html)
[Scala 2.10.1 +](http://scala-lang.org)
[Thrift 0.5.0](http://thrift.apache.org/)

### Tested on Ubuntu 13.04, AWS - EC2

## Usage

To build and test, run `mvn test`.

To package, run `mvn package`. This produces `snowflake-package-dist-zip` with the following.

`scripts` - Ruby script, snowflake startup script
`libs`    - Jar files as needed to run
`config`  - Configuration files written in scala.

### Configuration

A hard dependency to libthrift 0.5.0 was seen when invoked from 'Scala 2.10.1` .

Make sure `zookeeper` is running

Unzip the `snowflake-package-dist.zip`

Start `.\snowflake development.scala` from scripts dir.

Run `ruby client_test.rb`. (Move the thrift generated client files from `gen-rb` to `scripts` dir.)

A tested `scala 2.10.1` client is available. Refer the code from [megam_common](https:\\github.com\indykish\megam_common) 

Unfortunately `scala.reflect.Manifest` uses `runtimeClass` as opposed to `erasure`, so we built `UThriftClient`
and `USnowflakeClient`. 

If you are on 2.9.2 then you can use the `com.twitter.service.snowflake.SnowflakeClient` 

`UID` class uses `scalaz 7.0` and return `ValidationNel[Throwable, UniqueID tuple]` to the callee.

```java

import scalaz._
import scalaz.Validation._
import scalaz.NonEmptyList._

import Scalaz._
import org.apache.thrift.transport.{TTransport}

class UID(hostname: String, port: Int, agent: String, soTimeoutMS: Int = 20) {
  
  private lazy val service: UniqueIDService = 
    USnowflakeClient.create(hostname, port,soTimeoutMS)

  def get: ValidationNel[Throwable, UniqueID] = {
    (fromTryCatch {
      service._2.get_id(agent)
    } leftMap { t: Throwable => 
                   new Throwable(
                """Unique ID Generation failure for 'agent:' '%s'
            |
            |Please verify your ID Generation server host name ,port. Refer the stacktrace for more information
            |If this error persits, ask for help on the forums.""".format(agent).stripMargin + "\n ",t)      
    }).toValidationNel.flatMap { i: Long => Validation.success[Throwable, UniqueID](UniqueID(agent, i)).toValidationNel }
  }

}

object UID {

  def apply(host: String, port: Int, agent: String) = new UID(host, port, agent)

}


```

### Production (`api.megam.co` in our case)

* Chef cookbooks used at megam [megam chef-repo](https://github.com/indykish/chef-repo) - *work under progress

* Download the megamsnowflake_0.1.0.deb - *work under progress.

#### DEB Package using sbt.

The package structure shall be as per the debian guidelines. This uses sbt-native-packager plugin.

* `mvn clean`

Creates a `snowflake-package-dist.zip'

* `mvn package`

Start sbt

* `sbt`

Run task `sunburn` to unzip `snowflake-package-dist.zip into `target/debpkg` dir.

* `sunburn`

Generates the .deb package for this project.

* `sbt debian:package-bin`

### Upload the generated db to S3 [sbt-s3](https://github.com/sbt/sbt-s3)

Create a file named sbt_s3_key under a known location `~/software/aws/keys` with the appropriate values.


```
realm=Amazon S3
host=s3sbt-test.s3.amazonaws.com
user=xxx
password=xxx   
```

Tweak the following lines in build.sbt as you need. 

```java

mappings in upload := Seq((new java.io.File(("%s-%s.deb") format("target/megamsnowflake", "0.12.3-build-100")),"megamsnowflake-0.1.0.deb"))

host in upload := "s3sbt-test.s3.amazonaws.com"
```
Launch sbt

* `s3-upload`

# Contributing

To contribute:

1. fork the project
2. make a branch for each thing you want to do (don't put everything in your master branch: we don't want to cherry-pick and we may not want everything)
3. send a pull request to ryanking

