package armadillo.transformers.check;


import armadillo.transformers.base.BaseTransformer;
import armadillo.transformers.base.OtherTransformer;
import armadillo.utils.*;
import armadillo.utils.axml.EditXml.decode.AXMLDoc;
import armadillo.utils.axml.EditXml.editor.ContentProviderEditor;
import armadillo.utils.axml.EditXml.editor.PermissionEditor;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.immutable.reference.ImmutableMethodProtoReference;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.dexlib2.writer.pool.DexPool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;

public class VpnCheck extends OtherTransformer {
    @Override
    public void transform() throws Exception {
        String randon = nameFactory.randomName();
        byte[] axml = getReplacerRes().get("AndroidManifest.xml");
        if (axml == null)
            axml = StreamUtil.readBytes(getZipFile().getInputStream(new ZipEntry("AndroidManifest.xml")));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AXMLDoc axmlDoc = new AXMLDoc();
        axmlDoc.parse(new ByteArrayInputStream(axml));

        PermissionEditor permissionEditor = new PermissionEditor(axmlDoc);
        permissionEditor.setEditorInfo(new PermissionEditor.EditorInfo()
                .with(new PermissionEditor.PermissionInfo("android.permission.ACCESS_NETWORK_STATE"))
                .with(new PermissionEditor.PermissionInfo("android.permission.ACCESS_WIFI_STATE"))
                .with(new PermissionEditor.PermissionInfo("android.permission.INTERNET"))
                .with(new PermissionEditor.PermissionInfo("Armadillo")));
        permissionEditor.commit();


        ContentProviderEditor contentProviderEditor = new ContentProviderEditor(axmlDoc);
        contentProviderEditor.setEditorInfo(new ContentProviderEditor.Editorinfo()
                .with(new ContentProviderEditor.ProviderInfo("arm." + randon, randon, false, 19999999)));
        contentProviderEditor.commit();

        axmlDoc.build(outputStream);
        axmlDoc.release();
        getReplacerRes().put("AndroidManifest.xml", outputStream.toByteArray());

        byte[] bytes = LoaderRes.getInstance().getStaticResAsBytes("smali/VpnCheck.smali");
        String body = new String(bytes).replace("LArmadillo/VpnCheckProvider;",  String.format("Larm/%s;",randon));
        DexPool dexPool = new DexPool(Opcodes.getDefault());
        dexPool.internClass(SmaliUtils.assembleSmali(body.getBytes()));
        MemoryDataStore dataStore = new MemoryDataStore();
        dexPool.writeTo(dataStore);
        getAdd_Classdex().add(Arrays.copyOf(dataStore.getBuffer(), dataStore.getSize()));
        dataStore.close();
    }

    @Override
    public String getResult() {
        return null;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int compareTo(BaseTransformer o) {
        return 0;
    }
}
