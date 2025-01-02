cd sqlengine
mvn package -DskipTests
cd ../
mvn install:install-file \
    -Dfile=sqlengine/target/sqlengine-1.0-SNAPSHOT.jar \
    -DgroupId=com.sqlengine \
    -DartifactId=sqlengine \
    -Dversion=1.0-SNAPSHOT
