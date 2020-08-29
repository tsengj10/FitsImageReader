package org.lsst.fits.imageio.wcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author tonyj
 */
public class WCSReader {

    Map<String, Map<String, Object>> data = new HashMap<>();

    public WCSReader(File file) throws IOException {
        this(new FileInputStream(file));
    }
    
    public WCSReader(URL url) throws IOException {
        this(url.openStream());
    }

    public WCSReader(InputStream input) throws IOException {
        
        Properties props = new Properties();                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      
        props.load(input);
        input.close();
        String keys = props.get("legend").toString();
        if (keys == null) {
            throw new IOException("Missing legend in wCS file");
        }
        String[] keyList = keys.split("\\s*,\\s*");
        System.out.println(Arrays.toString(keyList));
        props.entrySet().stream().filter((e) -> !"legend".equals(e.getKey())).forEach(e -> {
            Map<String, Object> result = new HashMap<>();
            String key = e.getKey().toString();
            String value = e.getValue().toString();
            String[] values = value.split(",\\s+"); // FIXME
            for (int i=0; i<keyList.length; i++) {
                if (values[i].startsWith("[")) {
                    result.put(keyList[i],values[i]);
                } else {
                    double v = Double.parseDouble(values[i]);
                    result.put(keyList[i],v);
                }
            }
            data.put(key, result);
        });
    }

    public Map<String, Map<String, Object>> getWCSInfo() {                                                                    
        return data;
    }
}
