package util;

import io.github.cdimascio.dotenv.Dotenv;

public class ReadConfig {
    public static Dotenv dotenv = Dotenv.configure()
//            .directory("../../../resources")
            .filename("dev.config")     // specify a name other than ".env"
            .load();
}
