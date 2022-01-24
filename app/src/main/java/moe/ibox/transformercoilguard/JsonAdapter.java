package moe.ibox.transformercoilguard;

public class JsonAdapter {
    static class AzureBlobData {
        String EnqueuedTimeUtc;
        SystemPropertiesBean SystemProperties;
        BodyBean Body;

        static class SystemPropertiesBean {
            String connectionDeviceId;
            String connectionAuthMethod;
            String connectionDeviceGenerationId;
            String contentType;
            String contentEncoding;
            String enqueuedTime;
        }

        static class BodyBean {
//            double[] voltages;
//            double[] currents;
LissajousBean lissajous;
        }

        static class LissajousBean {
            LissajousAxis A;
            LissajousAxis B;
            LissajousAxis C;

            static class LissajousAxis {
                double[] X;
                double[] Y;
            }
        }
    }
}
