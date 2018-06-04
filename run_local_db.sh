#!/bin/sh
dir=$(dirname $(realpath $0))
cd $dir
sudo mkdir -p /var/lib/castest
sudo mkdir -p /var/lib/castest/db1
sudo mkdir -p /var/lib/castest/db2
sudo mkdir -p /var/lib/castest/db3
docker network create -d bridge --subnet 172.21.0.0/16 casstest
docker stop db1
docker rm db1

docker stop db2
docker rm db2

docker stop db3
docker rm db3

docker run --name db1 --restart no --memory=4g --net casstest --ip 172.21.0.11 -d --mount type=bind,source=/var/lib/castest/db1,destination=/var/lib/cassandra --env CASSANDRA_CLUSTER_NAME=telecomax --env CASSANDRA_START_RPC=true --env JVM_OPTS='-Xms1024m -Xmx1024m' --env CASSANDRA_SEEDS=172.21.0.11,172.21.0.12,172.21.0.13 cassandra:latest
docker run --name db2 --restart no --memory=4g --net casstest --ip 172.21.0.12 -d --mount type=bind,source=/var/lib/castest/db2,destination=/var/lib/cassandra --env CASSANDRA_CLUSTER_NAME=telecomax --env CASSANDRA_START_RPC=true --env JVM_OPTS='-Xms1024m -Xmx1024m' --env CASSANDRA_SEEDS=172.21.0.11,172.21.0.12,172.21.0.13 cassandra:latest
docker run --name db3 --restart no --memory=4g --net casstest --ip 172.21.0.13 -d --mount type=bind,source=/var/lib/castest/db3,destination=/var/lib/cassandra --env CASSANDRA_CLUSTER_NAME=telecomax --env CASSANDRA_START_RPC=true --env JVM_OPTS='-Xms1024m -Xmx1024m' --env CASSANDRA_SEEDS=172.21.0.11,172.21.0.12,172.21.0.13 cassandra:latest