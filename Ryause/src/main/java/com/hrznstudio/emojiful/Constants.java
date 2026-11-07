package com.hrznstudio.emojiful;

import com.hrznstudio.emojiful.api.Emoji;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Constants {

    public static final String MOD_ID = "clickgui";
    public static final String MOD_NAME = "Ryause Chat Emotes";
    public static final Logger LOG = LogManager.getLogger(MOD_NAME);

    public static final Map<String, List<Emoji>> EMOJI_MAP = new HashMap<>();
    public static final List<Emoji> EMOJI_LIST = new ArrayList<>();
    public static volatile boolean error = false;
}
