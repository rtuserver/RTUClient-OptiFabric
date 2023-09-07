package me.modmuss50.optifabric.compat.borderlessmining;

public class BorderlessMiningNewMixinPlugin extends BorderlessMiningMixinPlugin {
    @Override
    protected String getCreateSimpleOptionInitDescriptor() {
        return "(Ljava/lang/String;Lnet/minecraft/class_7172$class_7277;Lnet/minecraft/class_7172$class_7303;Lnet/minecraft/class_7172$class_7178;Ljava/lang/Object;Ljava/util/function/Consumer;)V";
    }
}
