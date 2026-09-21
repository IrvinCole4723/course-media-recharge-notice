package learning.streaming;

public final class StreamDeliveryApplication {
    public static void main(String[] args) {
        LayeredMediaConfig config = LayeredMediaConfig.fromEnvironment();
        CreatorDeliveryService service = new CreatorDeliveryService(new InfraiMediaGateway(config), config);
        MediaLessonAsset asset = new MediaLessonAsset("asset-bio-204", "Cell Division Lab", config.creatorEmail(), "transcode-bio-204");
        CreatorDeliveryService.CreatorDeliveryResult result = service.deliver(asset, true);
        System.out.println("Creator delivery stages: " + result.stages());
    }
}
