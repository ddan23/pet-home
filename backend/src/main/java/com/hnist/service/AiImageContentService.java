package com.hnist.service;

/** 把本站上传目录中的图片读成可传给多模态审核的 data URL 与内容哈希。 */
public interface AiImageContentService {

    /** 只接受 /uploads/ 下的本站相对路径；读不到、越界、超限或非普通文件一律返回 null，由审核方判为图片待人工复核。 */
    ImageContent load(String imageUrl);

    /** 图片内容载体：dataUrl 仅供本次模型调用、不落库，hash 是内容 SHA-256，供审核日志追溯同一张图。 */
    record ImageContent(String dataUrl, String hash) {
    }
}
