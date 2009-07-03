// License: GPL. Copyright 2007 by Immanuel Scholz and others
package org.openstreetmap.josm.gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openstreetmap.josm.actions.HelpAction.Helpful;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.Main;

class MapSlider extends JSlider implements PropertyChangeListener, ChangeListener, Helpful {

    private final MapView mv;
    boolean preventChange = false;

    public MapSlider(MapView mv) {
        super(35, 150);
        setOpaque(false);
        this.mv = mv;
        mv.addPropertyChangeListener("scale", this);
        addChangeListener(this);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (getModel().getValueIsAdjusting()) return;

        ProjectionBounds world = Main.proj.getWorldBounds();
        ProjectionBounds current = this.mv.getProjectionBounds();

        double cur_e = current.max.east()-current.min.east();
        double cur_n = current.max.north()-current.min.north();
        double e = world.max.east()-world.min.east();
        double n = world.max.north()-world.min.north();
        int zoom = 0;

        while(zoom <= 150) {
            e /= 1.1;
            n /= 1.1;
            if(e < cur_e && n < cur_n)
                break;
            ++zoom;
        }
        preventChange=true;
        setValue(zoom);
        preventChange=false;
    }

    public void stateChanged(ChangeEvent e) {
        if (preventChange) return;

        ProjectionBounds world = Main.proj.getWorldBounds();
        double fact = Math.pow(1.1, getValue());
        double es = world.max.east()-world.min.east();
        double n = world.max.north()-world.min.north();

        this.mv.zoomTo(new ProjectionBounds(this.mv.getCenter(), es/fact, n/fact));
    }

    public String helpTopic() {
        return "MapView/Slider";
    }
}