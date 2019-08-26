/**
 * Copyright (c) 2011, University of Konstanz, Distributed Systems Group All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met: * Redistributions of source code must retain the
 * above copyright notice, this list of conditions and the following disclaimer. * Redistributions
 * in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 * * Neither the name of the University of Konstanz nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sirix.node.json;

import static org.junit.Assert.assertEquals;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sirix.Holder;
import org.sirix.XdmTestHelper;
import org.sirix.api.PageTrx;
import org.sirix.exception.SirixException;
import org.sirix.node.NodeKind;
import org.sirix.node.SirixDeweyID;
import org.sirix.node.delegates.NodeDelegate;
import org.sirix.node.delegates.StructNodeDelegate;
import org.sirix.node.interfaces.Record;
import org.sirix.page.UnorderedKeyValuePage;
import com.google.common.hash.Hashing;

/**
 * Object record node test.
 */
public class JSONObjectKeyNodeTest {

  /** {@link Holder} instance. */
  private Holder mHolder;

  /** Sirix {@link PageTrxImpl} instance. */
  private PageTrx<Long, Record, UnorderedKeyValuePage> mPageWriteTrx;

  private int mNameKey;

  @Before
  public void setUp() throws SirixException {
    XdmTestHelper.closeEverything();
    XdmTestHelper.deleteEverything();
    mHolder = Holder.openResourceManager();
    mPageWriteTrx = mHolder.getResourceManager().beginPageTrx();
  }

  @After
  public void tearDown() throws SirixException {
    mPageWriteTrx.close();
    mHolder.close();
  }

  @Test
  public void testNode() throws IOException {
    // Create empty node.
    mNameKey = mPageWriteTrx.createNameKey("foobar", NodeKind.OBJECT_KEY);
    final String name = "foobar";

    final long pathNodeKey = 12;
    final NodeDelegate del = new NodeDelegate(14, 13, Hashing.sha256(), null, 0, SirixDeweyID.newRootID());
    final StructNodeDelegate strucDel = new StructNodeDelegate(del, 17L, 16L, 15L, 0L, 0L);
    final ObjectKeyNode node = new ObjectKeyNode(strucDel, mNameKey, name, pathNodeKey);
    node.setHash(node.computeHash());
    check(node);

    // Serialize and deserialize node.
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    node.getKind().serialize(new DataOutputStream(out), node, mPageWriteTrx);
    final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    final ObjectKeyNode node2 = (ObjectKeyNode) NodeKind.OBJECT_KEY.deserialize(new DataInputStream(in),
        node.getNodeKey(), null, mPageWriteTrx);
    check(node2);
  }

  private final void check(final ObjectKeyNode node) {
    // Now compare.
    assertEquals(14L, node.getNodeKey());
    assertEquals(13L, node.getParentKey());
    assertEquals(17L, node.getFirstChildKey());
    assertEquals(16L, node.getRightSiblingKey());

    assertEquals(mNameKey, node.getNameKey());
    assertEquals("foobar", node.getName());
    assertEquals(NodeKind.OBJECT_KEY, node.getKind());
    assertEquals(true, node.hasFirstChild());
    assertEquals(true, node.hasParent());
    assertEquals(true, node.hasRightSibling());
  }

}
