package org.frcteam2910.common.robot.subsystems;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import org.frcteam2910.common.drivers.SwerveModule;
import org.frcteam2910.common.math.MathUtils;
import org.frcteam2910.common.math.RigidTransform2;
import org.frcteam2910.common.math.Rotation2;
import org.frcteam2910.common.math.Vector2;
import org.frcteam2910.common.util.InterpolatingDouble;
import org.frcteam2910.common.util.InterpolatingTreeMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.crypto.dsig.Transform;

public abstract class SwerveDrivetrain extends HolonomicDrivetrain {
    private Vector2 kinematicPosition = Vector2.ZERO;
    private Vector2 kinematicVelocity = Vector2.ZERO;
    private double lastKinematicTimestamp;

    private InterpolatingTreeMap<InterpolatingDouble, Vector2> positionSamples = new InterpolatingTreeMap<>(5);

    
    public ArrayList<String>[] motorsPos =new ArrayList[4];
    public ArrayList<String> avgPos = new ArrayList<String>();
    public SwerveDrivetrain(){
        for (int i = 0; i < 4; i++) { 
            motorsPos[i] = new ArrayList<String>(); 
        } 
    }

    public void holonomicDrive(Vector2 translation, double rotation, boolean fieldOriented) {
        if (fieldOriented) {
            translation = translation.rotateBy(getGyroscope().getAngle().inverse());
        }

        for (SwerveModule module : getSwerveModules()) {
            Vector2 velocity = module.getModulePosition().normal().scale(rotation).add(translation);

            module.setTargetVelocity(velocity);
        }
    }

    public abstract SwerveModule[] getSwerveModules();

    @Override
    public void stop() {
        holonomicDrive(Vector2.ZERO, 0, true);
    }


    
    public synchronized void updateKinematics(double timestamp, boolean doSave) {
        double robotRotation = getGyroscope().getAngle().toRadians();
        double dt = timestamp - lastKinematicTimestamp;
        lastKinematicTimestamp = timestamp;

        SwerveModule[] swerveModules = getSwerveModules();

        Vector2 averageCenter = Vector2.ZERO;
        int i = 0;
        for (SwerveModule module : swerveModules) {
            module.updateSensors();
            module.updateKinematics(robotRotation);
            if (doSave)
            motorsPos[i].add("(" + module.getCurrentPosition().y + ", " + module.getCurrentPosition().x + ")\n");

            Vector2 estimatedCenter = new RigidTransform2(module.getCurrentPosition(),
                    Rotation2.fromRadians(robotRotation))
                    .transformBy(new RigidTransform2(module.getModulePosition().inverse(), Rotation2.ZERO)).translation;
            //System.out.println("estimated center x: " + estimatedCenter.x);
           // System.out.println("estimated center y: " + estimatedCenter.y);

            averageCenter = averageCenter.add(estimatedCenter);
            i++;
        }
        
        
        averageCenter = averageCenter.scale(1.0 / swerveModules.length);
        //System.out.println("average center: " + averageCenter);
        SmartDashboard.putNumber("Average Center X", averageCenter.x);
        SmartDashboard.putNumber("Average Center Y", averageCenter.y);
        //SmartDashboard.putNumber("Average center", averageCenter);
        positionSamples.put(new InterpolatingDouble(timestamp), averageCenter);

        {
            Map.Entry<InterpolatingDouble, Vector2> lastPosition = positionSamples.firstEntry();
            kinematicVelocity = averageCenter.subtract(lastPosition.getValue()).scale(1 / (timestamp - lastPosition.getKey().value));
        }
        kinematicPosition = averageCenter;

        if(doSave){
            avgPos.add("(" + kinematicPosition.y + ", " + kinematicPosition.x + ")\n");
        }

        for (SwerveModule module : swerveModules) {
            
            // module.resetKinematics(new RigidTransform2(kinematicPosition, Rotation2.fromRadians(robotRotation))
            
            //         .transformBy(new RigidTransform2(module.getModulePosition(), Rotation2.ZERO)).translation);

            Vector2 modulePosition = module.getModulePosition();

            RigidTransform2 transform = new RigidTransform2(modulePosition, Rotation2.ZERO);

            Rotation2 radianRobotRotation = Rotation2.fromRadians(robotRotation);
            
            RigidTransform2 bigString = new RigidTransform2(kinematicPosition, radianRobotRotation).transformBy(transform);

            Vector2 bigStringTranslation = bigString.translation;

            module.resetKinematics(bigStringTranslation);
            
            module.updateState(dt);
        }
    }

    /**
     * @deprecated Use {@link #resetKinematics(Vector2, double)} instead.
     */
    @Deprecated
    public synchronized void resetKinematics(double timestamp) {
        resetKinematics(Vector2.ZERO, timestamp);
    }

    public synchronized void resetKinematics(Vector2 position, double timestamp) {
        for (SwerveModule module : getSwerveModules()) {
            module.resetKinematics(position.add(module.getModulePosition()));
        }

        kinematicVelocity = Vector2.ZERO;
        kinematicPosition = position;
        lastKinematicTimestamp = timestamp;
    }

    @Override
    public Vector2 getKinematicPosition() {
        return kinematicPosition;
    }

    @Override
    public Vector2 getKinematicVelocity() {
        return kinematicVelocity;
    }

    @Override
    public void outputToSmartDashboard() {
        super.outputToSmartDashboard();
        for (SwerveModule module : getSwerveModules()) {
            SmartDashboard.putNumber(String.format("%s module angle", module.getName()), Math.toDegrees(module.getCurrentAngle()));
            SmartDashboard.putNumber(String.format("%s module drive distance", module.getName()), module.getCurrentDistance());
            SmartDashboard.putString(String.format("%s module position", module.getName()), module.getCurrentPosition().toString());
            SmartDashboard.putNumber(String.format("%s module velocity", module.getName()), module.getCurrentVelocity());
            SmartDashboard.putNumber(String.format("%s module drive current", module.getName()), module.getDriveCurrent() + Math.random() * MathUtils.EPSILON);
        }
    }
}
