package eu.koboo.en2do.internal.convention;

import eu.koboo.en2do.repository.entity.TransformField;
import eu.koboo.en2do.repository.entity.Transient;
import org.bson.codecs.pojo.ClassModelBuilder;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.PropertyModelBuilder;

import java.lang.annotation.Annotation;

public class TransformFieldConvention implements Convention {

    @Override
    public void apply(ClassModelBuilder<?> classModelBuilder) {
        for (PropertyModelBuilder<?> propertyModelBuilder : classModelBuilder.getPropertyModelBuilders()) {
            for (Annotation readAnnotation : propertyModelBuilder.getReadAnnotations()) {
                if(readAnnotation instanceof TransformField transformField) {
                    propertyModelBuilder.readName(transformField.value());
                }
            }
            for (Annotation writeAnnotation : propertyModelBuilder.getWriteAnnotations()) {
                if(writeAnnotation instanceof TransformField transformField) {
                    propertyModelBuilder.writeName(transformField.value());
                }
            }
        }
    }
}
