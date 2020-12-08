package com.clearstream.hydrogen.messagetransform;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Message;

@Slf4j
public class MessageProcessing {

   public static String convertBytesMessageToString(Message message) throws Exception {
       String bodyConvertedToString = null;

       byte[] body = message.getBody(byte[].class);
       if (body != null) {
           bodyConvertedToString = new String(message.getBody(byte[].class));
       } else {
           throw new Exception("Message contents does not contain byte stream");
       }
       return bodyConvertedToString;
   }

}
