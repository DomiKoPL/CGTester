# CGTester

## Usage

First copy the files from [/src](/src) to the referee.

### pom.xml

First change the gamengine version.

    <properties>
        <gamengine.version>4.4.2</gamengine.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
    </properties>

Second add new JCommander to dependencies.

    <dependency>
        <groupId>org.jcommander</groupId>
        <artifactId>jcommander</artifactId>
        <version>1.83</version>
    </dependency>


### Compile the referee

Next use rebuild.sh to compile the referee.

### Run

Now we can use [run.sh](run.sh) to run CGTester. 
The script will automatically find the target and will pass all the flags to CGTester.

Use `sh run.sh -h` to see all the options.