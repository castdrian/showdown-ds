package dev.adrian.showdown

import java.net.URLEncoder

object ScavioAnimatedSpriteIndex {
    private val mediaUrlPattern = Regex(
        """https://64\.media\.tumblr\.com/[^"\s]+?/s(?:640x960|1280x1920)/[^"\s]+?\.gif""",
        RegexOption.IGNORE_CASE
    )

    fun apiUrl(species: String): String =
        "https://scaviogifs.tumblr.com/api/read/json?tagged=${URLEncoder.encode(species.replace('-', ' '), Charsets.UTF_8.name())}"

    fun candidates(body: String): List<String> = mediaUrlPattern.findAll(body.replace("\\/", "/"))
        .map { it.value }
        .distinct()
        .toList()
}
