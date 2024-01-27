target=$(find target/ -maxdepth 1 -name "*.jar" )

if [[ -z "$target" ]]; then
    echo "Target not found."
    echo "Remeber to run ./rebuild.sh before running this command."
    exit 1
fi

java -cp ${target}:target/dependency/* cgtester.CGTester "$@"
