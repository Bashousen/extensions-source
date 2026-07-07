package eu.kanade.tachiyomi.animeextension.pt.dattebayobr

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DattebayoBR : AnimeHttpSource() {
    override val name = "Dattebayo BR"
    override val baseUrl = "https://www.dattebayo-br.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override fun headersBuilder(): okhttp3.Headers.Builder = okhttp3.Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        .add("Accept", "*/*")
        .add("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("X-Requested-With", "XMLHttpRequest")

    // Popular
    override fun popularAnimeRequest(page: Int): Request =
        GET(baseUrl, headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document
            .select("div.ultimosAnimesHomeItem")
            .map { element ->
                SAnime.create().apply {
                    val anchor = element.selectFirst("a")!!

                    title = element
                        .selectFirst(".ultimosAnimesHomeItemInfosNome")!!
                        .text()
                        .trim()

                    setUrlWithoutDomain(anchor.attr("href"))

                    thumbnail_url = element
                        .selectFirst("img")
                        ?.attr("abs:src")
                }
            }
        return AnimesPage(animes, hasNextPage = false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        popularAnimeRequest(page)

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document
            .select(".epiContainer:nth-child(4) > .ultimosEpisodiosHomeItem")
            .map { element ->
                SAnime.create().apply {
                    val anchor = element.selectFirst("a")!!

                    title = element.selectFirst(".ultimosEpisodiosHomeItemInfosNome")!!
                        .text()
                        .trim()

                    setUrlWithoutDomain(anchor.attr("href"))

                    thumbnail_url = element
                        .selectFirst("img")
                        ?.attr("abs:src")
                }
            }
        return AnimesPage(animes, hasNextPage = false)
    }

    // Search
    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request =
        GET("$baseUrl/busca?busca=$query&page=$page", headers)

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document
            .select("div.ultimosAnimesHomeItem")
            .map { element ->
                SAnime.create().apply {
                    val anchor = element.selectFirst("a")!!
                    title = element
                        .selectFirst(".ultimosAnimesHomeItemInfosNome")!!
                        .text()
                        .trim()

                    setUrlWithoutDomain(anchor.attr("href"))

                    thumbnail_url = element
                        .selectFirst("img")
                        ?.attr("abs:src")
                }
            }

        return AnimesPage(animes, hasNextPage(document))
    }

    // Details
    override fun animeDetailsParse(response: Response): SAnime {
        val document = getRealAnimeDoc(response.asJsoup())

        return SAnime.create().apply {
            title = document.selectFirst(".tituloPage h1")
                ?.text()
                ?.trim()
                ?: "Sem título"

            thumbnail_url = document
                .selectFirst(".aniInfosSingleCapa img")
                ?.attr("abs:src")

            description = document
                .selectFirst(".aniInfosSingleSinopse p")
                ?.text()
                ?.trim()

            genre = document
                .select(".aniInfosSingleGeneros span")
                .joinToString { it.text() }

            status = when (
                document.selectFirst(".anime_status span")
                    ?.text()
                    ?.lowercase()
            ) {
                "completo" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }

            initialized = true
        }
    }

    // Episodes
    private fun parseEpisodeElement(element: Element): SEpisode? {
        try {
            val anchor = element.selectFirst("a") ?: return null
            val episodeNumText = element.selectFirst(".ultimosEpisodiosHomeItemInfosNum")
                ?.text()
                ?.replace("Episódio", "")
                ?.trim()
                ?: return null

            val episodeNumber = episodeNumText
                .replace(",", ".")
                .toFloatOrNull()

            return SEpisode.create().apply {
                setUrlWithoutDomain(anchor.attr("href"))
                name = episodeNumText
                episode_number = episodeNumber ?: 0f
                date_upload = parseDate(element.selectFirst(".lancaster_episodio_info_data")?.text())
            }
        } catch (e: Exception) {
            return null
        }
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val episodes = mutableListOf<SEpisode>()
        val seenUrls = mutableSetOf<String>()

        val baseAnimeUrl = response.request.url.toString().takeIf { "videos" !in it }
            ?: response.asJsoup().selectFirst(animeMenuSelector)!!.parent()!!.attr("href")

        var currentPage = 1

        while (true) {
            val pageUrl = if (currentPage == 1) {
                baseAnimeUrl
            } else {
                "$baseAnimeUrl/page/$currentPage"
            }

            val responsePage = client.newCall(GET(pageUrl, headers)).execute()
            val document = responsePage.asJsoup()
            responsePage.close()

            val pageEpisodes = document.select("div.ultimosEpisodiosHomeItem")
            if (pageEpisodes.isEmpty()) break

            var addedAny = false

            pageEpisodes.forEach { element ->
                val episode = parseEpisodeElement(element) ?: return@forEach
                if (seenUrls.add(episode.url)) {
                    episodes.add(episode)
                    addedAny = true
                }
            }

            if (!addedAny) break
            if (!hasNextPage(document)) break

            currentPage++
        }

        if (episodes.isEmpty()) return emptyList()

        return episodes
            .sortedByDescending { ep ->
                ep.episode_number.takeIf { it > 0f } ?: Float.MAX_VALUE
            }
            .mapIndexed { index, ep ->
                ep.apply {
                    if (episode_number <= 0f) {
                        episode_number = (episodes.size - index).toFloat()
                    }
                }
            }
    }

    private fun parseDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L

        return try {
            val pattern = "dd/MM/yyyy 'às' HH:mm"
            val formatter = java.text.SimpleDateFormat(pattern, java.util.Locale("pt", "BR"))
            formatter.parse(dateText)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // Videos
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()
        val episodeUrl = response.request.url.toString()

        // 1. Select only the tabs that exist in HTML. This prevents the code from attempting to process “id 2” if it does not exist in AbasBox.
        val activeAbas = document.select("div.AbasBox div.Aba")

        activeAbas.forEach { aba ->
            val abaType = aba.attr("aba-type")
            val qualityName = aba.text().trim()

            // 2. Locate the player container corresponding to that tab.
            val container = document.getElementById(abaType) ?: return@forEach

            // 3. Extract the specific script from this container.
            val scriptData = container.selectFirst("script")?.data() ?: ""
            val vidRegex = """var vid\s*=\s*['"](.*?)['"]""".toRegex()
            val urlQualidade = vidRegex.find(scriptData)?.groupValues?.get(1) ?: return@forEach

            try {
                val encodedUrl = java.net.URLEncoder.encode(urlQualidade, "UTF-8")
                val adUrl = "https://ads.animeyabu.net?url=$encodedUrl"

                val adHeaders = headersBuilder()
                    .add("Referer", episodeUrl)
                    .add("Origin", "https://www.dattebayo-br.com")
                    .build()

                val adRequest = Request.Builder().url(adUrl).headers(adHeaders).build()
                val adResponse = client.newCall(adRequest).execute()
                val adBody = adResponse.body?.string() ?: ""
                adResponse.close()

                if (adBody.contains("publicidade")) {
                    val jsonArray = JSONArray(adBody)
                    if (jsonArray.length() > 0) {
                        val obj = jsonArray.getJSONObject(0)
                        val assinatura = obj.optString("publicidade", "")

                        if (assinatura.isNotBlank()) {
                            val urlFinal = urlQualidade + assinatura
                            videos.add(Video(urlFinal, qualityName, urlFinal, headers = adHeaders))
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent log so as not to interrupt the search for other qualities.
            }
        }

        return videos.sortedByDescending { it.quality }
    }

    // Utilities
    private fun hasNextPage(document: Document): Boolean {
        val currentPage = document.location().toHttpUrl()
        val lastPage = document.selectFirst("div.letterBox a:last-child")!!
            .attr("abs:href").toHttpUrl()

        return currentPage != lastPage
    }

    private val animeMenuSelector = ".controlesBoxItem .iconLista"

    private fun getRealAnimeDoc(document: Document): Document {
        val menu = document.selectFirst(animeMenuSelector)
        return if (menu != null) {
            val originalUrl = menu.parent()!!.attr("href")
            val req = client.newCall(GET(originalUrl, headers)).execute()
            req.asJsoup()
        } else {
            document
        }
    }
}
