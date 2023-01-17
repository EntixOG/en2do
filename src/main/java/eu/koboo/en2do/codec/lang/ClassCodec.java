package eu.koboo.en2do.codec.lang;

import org.bson.BsonInvalidOperationException;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@SuppressWarnings("rawtypes")
public class ClassCodec implements Codec<Class> {

    @Override
    public void encode(BsonWriter writer, Class value, EncoderContext encoderContext) {
        writer.writeString(value.getName());
    }

    @Override
    public Class decode(BsonReader reader, DecoderContext decoderContext) {
        String className = reader.readString();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new BsonInvalidOperationException("Cannot create class from name \"" + className + "\"", e);
        }
    }

    @Override
    public Class<Class> getEncoderClass() {
        return Class.class;
    }
}
