import com.datastax.driver.core.Cluster
import com.datastax.driver.core.ConsistencyLevel
import com.datastax.driver.core.querybuilder.QueryBuilder
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class TestConcurency {
    val logger = Logger.getLogger("CASTEST")

    companion object {
        val cluster = Cluster.builder()
                .withClusterName("castest")
                .addContactPoints("172.21.0.11", "172.21.0.12", "172.21.0.13")
                .build()
                .apply {
                    connect().also {
                        it.execute("create keyspace if not exists tests with replication = {'class': 'SimpleStrategy', 'replication_factor' : 2 }")
                        it.close()
                    }
                }

        val session = cluster.connect("tests")

        @JvmStatic
        @BeforeClass
        fun initTests() {
            doneTests()
            session.execute("create table if not exists lock(id text primary KEY,owner text)")
        }

        @AfterClass
        fun doneTests() {
            session.execute("drop table if exists lock")
        }
    }

    /***
     * Try to lock ID and returns 1 on success and 0 on failure (try repeat on exception)
     */
    fun singleLock(id: String): Int {
        while (true) try {
            return session.execute(
                    QueryBuilder.insertInto("lock")
                            .value("id", id)
                            .value("owner", "me")
                            .ifNotExists()
                            .apply {
                                serialConsistencyLevel = ConsistencyLevel.SERIAL
                            }
            )
                    .wasApplied()
                    .takeIf { it }
                    ?.let { 1 }
                    ?: 0
        } catch (e: Throwable) {
            logger.info(e.message)
            continue
        }
    }

    @Test
    fun testConcurency() {
        val count = 1000
        val parallelism = 3
        Observable.intervalRange(0, count.toLong(), 0, 0, TimeUnit.MILLISECONDS)
                .map { UUID.randomUUID().toString() }
                .subscribeOn(Schedulers.io())
                .flatMap {
                    Observable.just(it).repeat(parallelism.toLong())
                            .subscribeOn(Schedulers.io())
                            .flatMap {
                                Observable.fromCallable { singleLock(it) }
                                        .subscribeOn(Schedulers.io())
                            }
                            .subscribeOn(Schedulers.io())
                            .reduce(0, { t1: Int, t2: Int -> t1 + t2 })
                            .toObservable()
                }
                .subscribeOn(Schedulers.io())
                .reduce(0, { t1: Int, t2: Int -> t1 + t2 })
                .blockingGet()
                .also {
                    logger.info("Unique locks: $it of $count")
                    assert(it == count)
                }
    }
}
