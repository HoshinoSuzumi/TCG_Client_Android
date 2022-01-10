package moe.ibox.transformercoilguard;

public class XMLAdapter {
    static class BlobEnumList {
        EnumerationResultsBean EnumerationResults;

        static class EnumerationResultsBean {
            BlobBean[] Blobs;
        }

        static class BlobBean {
            String Name;

            public String getName() {
                return Name;
            }

            PropertiesBean Properties;

            static class PropertiesBean {
                String ETag;
            }
        }
    }
}
