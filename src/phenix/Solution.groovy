package phenix

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import org.joda.time.LocalDate
import org.joda.time.format.ISODateTimeFormat

import java.text.SimpleDateFormat

/**
 * Created by alex on 05/03/15.
 */
class Solution {

    static class Stat {

        static class Daily {
            class Shop {
                class Product {
                    String id

                    int qty

                    double ca

                    int accQty

                    double accCa

                    def add(int n, double pu) {
                        qty += n
                        ca += n * pu
                    }

                    def acc(int n, double pu) {
                        accQty += n
                        accCa += n * pu
                    }

                    String toString() {
                        "$id ($qty, $ca, $accQty, $accCa)"
                    }
                }

                String id

                Map<String, Product> products = [:].withDefault { new Product(id: it) }

                List<Product> getTop100() {
                    def results = products.values().sort { it.qty }.reverse()
                    results.subList(0, Math.min(100, results.size()))
                }

                List<Product> getTop100CA() {
                    def results = products.values().sort { it.ca }.reverse()
                    results.subList(0, Math.min(100, results.size()))
                }

                List<Product> getTop100Acc() {
                    def results = products.values().sort { it.accQty }.reverse()
                    results.subList(0, Math.min(100, results.size()))
                }

                List<Product> getTop100AccCA() {
                    def results = products.values().sort { it.accCa }.reverse()
                    results.subList(0, Math.min(100, results.size()))
                }

                String toString() {
                    top100
                }
            }

            LocalDate id

            Map<String, Shop> shops = [:].withDefault { new Shop(id: it) }

            String toString() {
                shops.toMapString()
            }
        }

        Map<LocalDate, Daily> daylies = [:].withDefault { new Daily(id: it) }

        String toString() {
            daylies.toMapString()
        }

    }

    static final int TXID = 0
    static final int DATETIME = 1
    static final int MAGASIN = 2
    static final int PROD_FK = 3
    static final int QTE = 4

    static final int PROD_ID = 0
    static final int PRIX = 1

    static final GLOBAL = "GLOBAL"

    static void main(String[] args) {
        /*  - txId : id de transaction (nombre)
        - datetime : date et heure sous le format Iso 8601
        - magasin : UUID identifiant le magasin
        - produit : id du produit (nombre)
        - qte : quantité (nombre)
        - prix : prix du produit en euros */

        def isoFmt = ISODateTimeFormat.basicDateTimeNoMillis()

        def stats = new Stat()

        def refFile = "data/reference_prod-8e588f2f-d19e-436c-952f-1cdd9f0b12b0_20150114.data"
        def dataFile = "data/transactions_20150114.data"

        def reader = new CSVReader(new FileReader(dataFile), '|' as char)

        def productRows = new CSVReader(new FileReader(refFile), '|' as char).readAll()
        def products = productRows.collectEntries { row -> [(row[PROD_ID]): row[PRIX] as double] }

        String[] row;
        while ((row = reader.readNext()) != null) {
            def day = isoFmt.parseDateTime(row[DATETIME]).toLocalDate()
            def shopId = row[MAGASIN]
            def productId = row[PROD_FK]
            int qty = row[QTE] as int

            double unitPrice = products[productId] ?: -1
            if (unitPrice >= 0) {
                stats.daylies[day].shops[shopId].products[productId].add(qty, unitPrice)
                stats.daylies[day].shops[GLOBAL].products[productId].add(qty, unitPrice)

                // cumulative 7 days period
                0..6.each { i ->
                    def date = day.plusDays(i)
                    stats.daylies[date].shops[shopId].products[productId].acc(qty, unitPrice)
                    stats.daylies[date].shops[GLOBAL].products[productId].acc(qty, unitPrice)
                }

            } else {
                println("ERROR: Impossible to determine price for product id: $productId")
            }
        }

        println stats

        def sortedDailyStats = stats.daylies.values().sort { it.id }.reverse()

        // prune and make output dir
        new File("output/").deleteDir()
        new File("output/").mkdir()

        // top_100_ventes_<MAGASIN_ID>_YYYYMMDD.data
        // top_100_ventes_GLOBAL_YYYYMMDD.data`
        sortedDailyStats.each { d ->
            def dateLabel = d.id.toDate().format("YYYYMMdd")
            d.shops.values().each { s ->
                new File("output/top_100_ventes_${s.id}_${dateLabel}.data").withWriter { w ->
                    new CSVWriter(w).writeAll(s.top100.collect { [it.id] as String[] })
                }
                new File("output/top_100_ca_${s.id}_${dateLabel}.data").withWriter { w ->
                    new CSVWriter(w).writeAll(s.top100CA.collect { [it.id] as String[] })
                }
                new File("output/top_100_ventes_${s.id}_${dateLabel}-J7.data").withWriter { w ->
                    new CSVWriter(w).writeAll(s.top100Acc.collect { [it.id] as String[] })
                }
                new File("output/top_100_ca_${s.id}_${dateLabel}-J7.data").withWriter { w ->
                    new CSVWriter(w).writeAll(s.top100AccCA.collect { [it.id] as String[] })
                }
            }


        }
    }
}
