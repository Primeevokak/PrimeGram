package vpn.tunnel

import org.amnezia.awg.config.Config

object ConfigMapper {

    fun parseConfig(rawConfig: String): Config {
        return Config.parse(rawConfig.reader().buffered())
    }
}
